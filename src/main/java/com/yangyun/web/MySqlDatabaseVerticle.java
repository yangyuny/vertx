package com.yangyun.web;

import io.vertx.core.*;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.asyncsql.MySQLClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.UpdateResult;

import java.io.IOException;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;



public class MySqlDatabaseVerticle extends AbstractVerticle{
    public static final String CONFIG_MYSQLDB_JDBC_URL = "jdbc:mysql://47.95.210.226:3306/vertx?queryTimeout=10000L;charset=UTF-8";
    public static final String CONFIG_MYSQLDB_JDBC_DRIVER_CLASS = "com.mysql.jdbc.Driver";
    public static final String CONFIG_MYSQLDB_JDBC_MAX_POOL_SIZE = "30";
    public static final String CONFIG_MYSQLDB_USER_NAME = "root";
    public static final String CONFIG_MYSQLDB_USER_PASSWORD = "123456";

    public static final String CONFIG_USERDB_SQL_QUERIES_RESOURCE_FILE = "wikidb.sqlqueries.resource.file";


    public static final String CONFIG_MYSQLDB_QUEUE = "mysqldb.queue";

    private static final Logger LOGGER = LoggerFactory.getLogger(MySqlDatabaseVerticle.class);

    // tag::loadSqlQueries[]
    private enum SqlQuery {
        GET_USER,
        CREATE_USERS_REG_TABLE,
        CREATE_USERS_LOGIN_TABLE,
        CREATE_USERS_INFO_TABLE,
        INSERT_REG_TABLE,
        INSERT_LOGIN_TABLE,
        INSERT_INFO_TABLE
    }

    private final HashMap<SqlQuery, String> sqlQueries = new HashMap<>();

    //加载所有的sql
    private void loadSqlQueries() throws IOException {
        String queriesFile = config().getString(CONFIG_USERDB_SQL_QUERIES_RESOURCE_FILE);
        InputStream queriesInputStream;
        if (queriesFile != null) {
            queriesInputStream = new FileInputStream(queriesFile);
        } else {
            queriesInputStream = getClass().getResourceAsStream("/db-queries.properties");
        }
        Properties queriesProps = new Properties();
        queriesProps.load(queriesInputStream);
        queriesInputStream.close();

        sqlQueries.put(SqlQuery.CREATE_USERS_REG_TABLE, queriesProps.getProperty("create-users-reg-table"));
        sqlQueries.put(SqlQuery.CREATE_USERS_LOGIN_TABLE, queriesProps.getProperty("create-users-login-table"));
        sqlQueries.put(SqlQuery.CREATE_USERS_INFO_TABLE, queriesProps.getProperty("create-users-info-table"));
        sqlQueries.put(SqlQuery.INSERT_REG_TABLE, queriesProps.getProperty("insert-reg-table"));
        sqlQueries.put(SqlQuery.INSERT_LOGIN_TABLE, queriesProps.getProperty("insert-login-table"));
        sqlQueries.put(SqlQuery.INSERT_INFO_TABLE, queriesProps.getProperty("insert-info-table"));
        sqlQueries.put(SqlQuery.GET_USER, queriesProps.getProperty("get-user"));
    }

    private SQLClient sqlClient;
    @Override
    public void start(Future<Void> startFuture) throws Exception {//连接mysql数据库
        loadSqlQueries();
        Future<Void> future = Future.future();
        sqlClient = MySQLClient.createShared(vertx, new JsonObject()
                .put("url", CONFIG_MYSQLDB_JDBC_URL)
                .put("class", CONFIG_MYSQLDB_JDBC_DRIVER_CLASS)
                .put("username", CONFIG_MYSQLDB_USER_NAME)
                .put("password", CONFIG_MYSQLDB_USER_PASSWORD)
                .put("max_pool_size", CONFIG_MYSQLDB_JDBC_MAX_POOL_SIZE));

        sqlClient.getConnection(ar -> {    // <5>
            if (ar.failed()) {
                LOGGER.error("Could not open a database connection", ar.cause());
                startFuture.fail(ar.cause());
            } else {
                SQLConnection connection = ar.result();

                connection.execute(sqlQueries.get(SqlQuery.CREATE_USERS_REG_TABLE), create -> {   // <2>
                    connection.execute(sqlQueries.get(SqlQuery.CREATE_USERS_LOGIN_TABLE), create2 -> {
                        connection.execute(sqlQueries.get(SqlQuery.CREATE_USERS_INFO_TABLE), create3 -> {
                            connection.close();
                            if (create.failed()) {
                                LOGGER.error("Database preparation error", create.cause());
                                startFuture.fail(create.cause());
                            } else {
                                vertx.eventBus().consumer(config().getString(CONFIG_MYSQLDB_QUEUE, "mysqldb.queue"), this::onMessage);// <3>
                                startFuture.complete();
                            }
                        });
                    });
                });
            }
        });
    }

    // tag::onMessage[]
    public enum ErrorCodes {
        NO_ACTION_SPECIFIED,
        BAD_ACTION,
        DB_ERROR
    }

    //根据HttpServerVerticle传过来的参数确定调用哪个方法
    public void onMessage(Message<JsonObject> message) {
        if (!message.headers().contains("action")) {
            LOGGER.error("No action header specified for message with headers {} and body {}",
                    message.headers(), message.body().encodePrettily());
            message.fail(ErrorCodes.NO_ACTION_SPECIFIED.ordinal(), "No action header specified");
            return;
        }
        String action = message.headers().get("action");
        switch (action) {
            case "get-user":
                fetchUser(message);
                break;
            case "insert-reg-table":
                InsertRegUser(message);
                break;
            default:
                message.fail(ErrorCodes.BAD_ACTION.ordinal(), "Bad action: " + action);
        }
    }

    //登录
    private void fetchUser(Message<JsonObject> message) {
        JsonObject request = message.body();
        String userName = message.body().getString("userName");
        sqlClient.getConnection(car -> {
            if (car.succeeded()) {
                SQLConnection connection = car.result();
                connection.queryWithParams(sqlQueries.get(SqlQuery.GET_USER), new JsonArray().add(userName), fetch -> {
                    connection.close();
                    if (fetch.succeeded()) {
                        JsonObject response = new JsonObject();
                        ResultSet resultSet = fetch.result();
                        if (resultSet.getNumRows() == 0){
                            response.put("found",false);
                        }else {
                            response.put("found",true);
                            JsonArray row = resultSet.getResults().get(0);
                            response.put("username", row.getString(0));
                            response.put("password", row.getString(1));
                        }
                        message.reply(response);
                    } else {
                        reportQueryError(message, fetch.cause());
                    }
                });
            } else {
                reportQueryError(message, car.cause());
            }
        });

    }


    //注册，将注册信息插入到user_reg、user_login、user_info三张表中
    private void InsertRegUser(Message<JsonObject> message) {
        JsonObject request = message.body();
        sqlClient.getConnection((AsyncResult<SQLConnection> car) -> {
            if (car.succeeded()) {
                SQLConnection connection = car.result();
                UUID uuid = UUID.randomUUID();
                String md5hash = "1";
                try {
                    md5hash = getMD5(uuid.toString());
                } catch (Exception e) {
                    e.printStackTrace();
                    reportQueryError(message, e.getCause());
                }
                //对用户注册信息进行分表操作
                String tableSuffixStr = !md5hash.equals("1") ? md5hash.substring(md5hash.length() - 1) : "1";
                Integer tableSuffixInt = Integer.valueOf(tableSuffixStr, 16) % 4;
                tableSuffixStr = tableSuffixInt.toString();

                JsonArray regdata = new JsonArray();
                regdata.add(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
                regdata.add(uuid.toString());
                regdata.add(request.getString("userName"));
                regdata.add(request.getString("passWord"));
                regdata.add(request.getString("sex"));

                JsonArray logindata = new JsonArray()
                        .add(uuid.toString())
                        .add(request.getString("userName"))
                        .add(request.getString("passWord"))
                        .add(request.getString("sex"))
                        .add("")
                        .add(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));

                JsonArray infodata = new JsonArray()
                        .add(uuid.toString())
                        .add("")
                        .add("");

                String insertRegSql = sqlQueries.get(SqlQuery.INSERT_REG_TABLE).replace("_@#@", "_" + tableSuffixStr);
                String insertInfoSql = sqlQueries.get(SqlQuery.INSERT_INFO_TABLE).replace("_@#@", "_" + tableSuffixStr);

                /*// insert user_reg
                Future<UpdateResult> CreateUserFuture = Future.future();
                connection.updateWithParams(sqlQueries.get(SqlQuery.INSERT_REG_TABLE), regdata, CreateUserFuture.completer());

                //insert user_info
                Future<UpdateResult> CreateInfoFuture = Future.future();
                connection.updateWithParams(sqlQueries.get(SqlQuery.INSERT_INFO_TABLE),infodata,CreateInfoFuture.completer());

                //insert user_login
                Future<UpdateResult> CreateLoginFuture = Future.future();
                connection.updateWithParams(sqlQueries.get(SqlQuery.INSERT_LOGIN_TABLE),logindata, CreateLoginFuture.completer());

                CompositeFuture.all(CreateUserFuture,CreateInfoFuture,CreateLoginFuture).setHandler(ar -> {
                    if (ar.succeeded()) {
                        message.reply("ok");
                    } else {
                        reportQueryError(message, ar.cause());
                    }
                });*/
                connection.updateWithParams(sqlQueries.get(SqlQuery.INSERT_REG_TABLE),regdata,res1 -> {
                    if (res1.succeeded()){
                        connection.updateWithParams(sqlQueries.get(SqlQuery.INSERT_LOGIN_TABLE),logindata,res2 -> {
                            if (res2.succeeded()){
                                connection.updateWithParams(sqlQueries.get(SqlQuery.INSERT_INFO_TABLE),infodata,res3 -> {
                                    if (res3.succeeded()){
                                        message.reply("ok");
                                    } else {
                                        reportQueryError(message, res3.cause());
                                    }
                                });
                            }else {
                                reportQueryError(message, res2.cause());
                            }
                        });
                    } else {
                        reportQueryError(message, res1.cause());
                    }
                });

            } else {
                reportQueryError(message, car.cause());
            }
        });
    }

    private String getMD5(String str) throws Exception {
        try {
            // 生成md5加密
            MessageDigest md = MessageDigest.getInstance("MD5");
            // 计算md5函数
            md.update(str.getBytes());
            // digest()最后确定返回md5 hash值，返回值为8为字符串。因为md5 hash值是16位的hex值，实际上就是8位的字符
            // BigInteger函数则将8位的字符串转换成16位hex值，用字符串来表示；得到字符串形式的hash值
            return new BigInteger(1, md.digest()).toString(16);
        } catch (Exception e) {
            throw new Exception("MD5加密出现错误");
        }
    }

    private void reportQueryError(Message<JsonObject> message, Throwable cause) {
        LOGGER.error("Database query error", cause);
        message.fail(ErrorCodes.DB_ERROR.ordinal(), cause.getMessage());
    }
}