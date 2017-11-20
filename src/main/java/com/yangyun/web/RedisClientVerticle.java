package com.yangyun.web;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.redis.RedisClient;
import io.vertx.redis.RedisOptions;

public class RedisClientVerticle extends AbstractVerticle{
    @Override
    public void start(Future<Void> startFuture) throws Exception {
//        super.start(startFuture);
        String host = Vertx.currentContext().config().getString("host");
        if (host == null) {
            host = "47.95.210.226";
        }
        final RedisClient client = RedisClient.create(vertx,new RedisOptions().setHost(host));
        client.set("key", "value", r -> {
            if (r.succeeded()) {
                System.out.println("key stored");
                client.get("key", s -> {
                    System.out.println("Retrieved value: " + s.result());
                });
            } else {
                System.out.println("Connection or Operation Failed " + r.cause());
            }
        });

    }
}