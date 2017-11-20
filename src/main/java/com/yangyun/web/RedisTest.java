package com.yangyun.web;

import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.redis.RedisClient;
import io.vertx.redis.RedisOptions;

/*
* 统计影院的收入情况以及卖出的影票的座位表，具体分析看根目录下的影院购票
* */
public class RedisTest{

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisTest.class);

    public static void main(String[] args) {
        RedisOptions config = new RedisOptions()
                .setHost("47.95.210.226").setPort(6379).setEncoding("UTF-8");
        System.out.println("config==="+config);
        Vertx vertx = Vertx.vertx();
        RedisClient redis = RedisClient.create(vertx, config);
        redis.set("index:userlogin:zhangsan","105",res -> {
            System.out.println("res==="+res);
            if (res.toString()=="OK"){
                System.out.println("res==="+res);
            } else {
                System.out.println("error===");
            }
        });
        /*redis.set("A","A[2]",res1 ->{
            redis.set("A","A[5]",res2 ->{
                redis.set("A","A[6]",res3 ->{
                    if (res3.succeeded()){
                        redis.get("A",res ->{
                            if (res.succeeded()){
                                LOGGER.info("卖出的影票数");
                            }else {
                                LOGGER.error("不明原因失败了");
                            }
                        });
                    }else {
                        LOGGER.error("error");
                    }
                });
            });
        });*/
    }
}