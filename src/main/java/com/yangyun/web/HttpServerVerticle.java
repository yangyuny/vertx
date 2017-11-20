package com.yangyun.web;

import com.yangyun.auth.YcAuthProvider;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.templ.ThymeleafTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;


public class HttpServerVerticle extends AbstractVerticle{
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpServerVerticle.class);

    public static final String CONFIG_HTTP_SERVER_PORT = "http.server.port";  // <1>
    public static final String CONFIG_MYSQLDB_QUEUE = "mysqldb.queue";

    private String mysqlDbQueue = "mysqldb.queue";
    ThymeleafTemplateEngine templateEngine = ThymeleafTemplateEngine.create();

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        mysqlDbQueue = config().getString(CONFIG_MYSQLDB_QUEUE, "mysqldb.queue");// <2>

        HttpServer server = vertx.createHttpServer();
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        router.get("/").handler(this::indexHandler);
        router.get("/login").handler(this::ToLoginHandler);
        router.get("/register").handler(this::ToRegHandler);
        router.get("/api/wx").handler(this::ToWeiXinHandler);
        router.get("/personinfo").handler(this::ToPersonInfoPage);
        router.get("/productInfo").handler(this::ToProductInfoPage);
//        router.get("/suggestion").handler(this::ToSuggestionPage);
        router.route("/*").handler(StaticHandler.create()); // static file
        router.post("/registerUser").handler(this::pageRegHandler);
        router.post("/loginUser").handler(this::pageLoginHandler);

        //抽取公共方法,,
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("/webroot/pages");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML5");
        resolver.setCharacterEncoding("UTF-8");
        templateEngine.getThymeleafTemplateEngine().setTemplateResolver(resolver);

        int portNumber = config().getInteger(CONFIG_HTTP_SERVER_PORT, 8080);
        server
                .requestHandler(router::accept)
                .listen(portNumber, ar -> {
                    if (ar.succeeded()) {
                        LOGGER.info("HTTP server running on port " + portNumber);
                        startFuture.complete();
                    } else {
                        LOGGER.error("Could not start a HTTP server", ar.cause());
                        startFuture.fail(ar.cause());
                    }
                });
    }

    private void ToWeiXinHandler(RoutingContext context) {
        EventBus eventBus = vertx.eventBus();
        DeliveryOptions options = new DeliveryOptions();
        options.addHeader("action", "getAccessToken");
        eventBus.send("api.weixin", "Yay! Someone kicked a ball across a patch of grass",options, ar -> {
            if (ar.succeeded()) {
                context.response().setStatusCode(200);
                context.response().putHeader("Content-type","text/json");

                context.response().end(ar.result().body().toString());
            }
        });

    }

    // 将所有的header抽取出来
    private void getType(RoutingContext context) {
        context.response().putHeader("Content-Type", "text/html;charset=utf-8");
        context.response().putHeader("Accept-Charset","utf-8");
    }




    private void indexHandler(RoutingContext context) {

//        templateEngine.render(context, "/index", res -> {});

        YcAuthProvider provider = new YcAuthProvider(vertx);
        provider.authenticate(new JsonObject().put("username","qwe"),res ->{
            User admin = res.result();
        });

        templateEngine.render(context, "", "/index", ar -> {
            if (ar.succeeded()) {
                context.response().end(ar.result());
            } else {
                context.fail(ar.cause());
            }
        });
    }

    private void ToRegHandler(RoutingContext context) {
        templateEngine.render(context,"","/register",ar -> {
            if (ar.succeeded()) {
                context.response().end(ar.result());
            } else {
                context.fail(ar.cause());
            }
        });
    }

    private void ToLoginHandler(RoutingContext context) {
        context.put("title","login page");
        templateEngine.render(context,"","/login",ar -> {
            if (ar.succeeded()) {
                context.response().putHeader("Content-Type", "text/html;charset=utf-8");
                context.response().putHeader("Accept-Charset","utf-8");
                context.response().end(ar.result());
            } else {
                context.fail(ar.cause());
            }
        });
    }


    //登录的handler,登录成功，跳到首页，登录失败，还是停留在登录页面
    private void pageLoginHandler(RoutingContext context) {
        String userName = context.request().getParam("username");
        String passWord = context.request().getParam("password");
        JsonObject jsonObject = new JsonObject();
        jsonObject.put("userName",userName);
        jsonObject.put("passWord",passWord);
        LOGGER.info("=====================================");
        LOGGER.info("jsonObject.toString==="+jsonObject.toString());
        DeliveryOptions options = new DeliveryOptions().addHeader("action", "get-user");

        vertx.eventBus().send(mysqlDbQueue, jsonObject, options, reply -> {
            if (reply.succeeded()){
                JsonObject body = (JsonObject) reply.result().body();
                String name = body.getString("username", "");
                String pwd = body.getString("password", "");
                LOGGER.info("name------------"+name);
                if (passWord.equals(pwd)){
                    context.put("info", "登录成功！");
                    context.put("name",name);
                    templateEngine.render(context, "","/index", ar -> {
                        if (ar.succeeded()) {
                            context.response().setStatusCode(303);
                            getType(context);
                            context.response().end(ar.result());
                        } else {
                            context.fail(ar.cause());
                        }
                    });
                }else {
                    context.put("msg", "用户名或密码不正确");
                    templateEngine.render(context, "","/login", ar -> {
                        if (ar.succeeded()) {
                            context.response().putHeader("Content-Type", "text/html;charset=utf-8");
                            context.response().end(ar.result());
                        } else {
                            context.fail(ar.cause());
                        }
                    });
                }
            }else {
                LOGGER.error("login feat");
                context.fail(reply.cause());
            }
        });
    }

    //注册页面，注册成功，直接跳到首页,注册失败，还停留在注册页面
    private void pageRegHandler(RoutingContext context) {
        DeliveryOptions options2 = new DeliveryOptions().addHeader("action", "insert-reg-table");
        String userName = context.request().getParam("username");
        String passWord = context.request().getParam("password");
        String conPassWord = context.request().getParam("conpassword");

        String sex = context.request().getParam("sex");
        JsonObject jsonObject = new JsonObject();
        jsonObject.put("userName",userName);
        jsonObject.put("passWord",passWord);
        jsonObject.put("conPassWord",conPassWord);
        jsonObject.put("sex",sex);

        DeliveryOptions options = new DeliveryOptions().addHeader("action", "get-user");

        vertx.eventBus().send(mysqlDbQueue, jsonObject, options, reply -> {
            if (reply.succeeded()){
                JsonObject body = (JsonObject) reply.result().body();
                String name = body.getString("username", "");
                if (name!=""){
                    LOGGER.info("=+++++++++++++++++++++++++++++++");
                    context.put("msg","用户名已存在");
                    templateEngine.render(context, "", "/register", ar -> {
                        if (ar.succeeded()) {
                            context.response().end(ar.result());
                        } else {
                            context.fail(ar.cause());
                        }
                    });
                }else {
                    if (passWord.equals(conPassWord)){
                        context.put("name",userName);
                        vertx.eventBus().send(mysqlDbQueue, jsonObject, options2, reply2 -> {
                            LOGGER.info("-----------------------------------------------");
                            if (reply.succeeded()){
                                templateEngine.render(context, "","/index", ar -> {
                                    if (ar.succeeded()) {
                                        getType(context);
                                        context.response().end(ar.result());
                                    } else {
                                        context.fail(ar.cause());
                                    }
                                });
                            }else {
                                context.fail(reply.cause());
                            }
                        });
                    }else {
                        context.put("msg","密码和确认密码不一样");
                        templateEngine.render(context, "", "/register", ar -> {
                            if (ar.succeeded()) {
                                context.response().putHeader("Content-Type", "text/html;charset=utf-8");
                                context.response().end(ar.result());
                            } else {
                                context.fail(ar.cause());
                            }
                        });
                    }

                }
            }
        });

    }

    //to  personinfo.html
    private void ToPersonInfoPage(RoutingContext context) {
        context.put("title","personinfo page");
        templateEngine.render(context,"","/personinfo",ar -> {
            if (ar.succeeded()) {
                getType(context);
                context.response().end(ar.result());
            } else {
                context.fail(ar.cause());
            }
        });
    }

    // to ToSuggestionPage
    /*private void ToSuggestionPage(RoutingContext context) {
        context.put("title","personinfo page");
        templateEngine.render(context,"","/suggestion",ar -> {
            if (ar.succeeded()) {
                getType(context);
                context.response().end(ar.result());
            } else {
                context.fail(ar.cause());
            }
        });
    }*/

    //ToProductInfoPage
    private void ToProductInfoPage(RoutingContext context) {
        context.put("title","productInfo page");
        templateEngine.render(context,"","/productInfo",ar -> {
            if (ar.succeeded()) {
                getType(context);
                context.response().end(ar.result());
            } else {
                context.fail(ar.cause());
            }
        });
    }

}