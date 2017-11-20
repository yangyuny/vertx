package com.yangyun.web;

import com.yangyun.wx.WeiXinVerticle;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;

public class MainVerticle extends AbstractVerticle {

    @Override
    public void start(Future<Void> startFuture) throws Exception {

        Future<String> dbVerticleDeployment = Future.future();  // <1>
        vertx.deployVerticle(new MySqlDatabaseVerticle(), dbVerticleDeployment.completer());  // <2>
        vertx.deployVerticle(new WeiXinVerticle(), dbVerticleDeployment.completer());
        dbVerticleDeployment.compose(id -> {  // <3>

            Future<String> httpVerticleDeployment = Future.future();
            vertx.deployVerticle(
                    "com.yangyun.web.HttpServerVerticle",  // <4>
                    new DeploymentOptions().setInstances(2),    // <5>
                    httpVerticleDeployment.completer());

            return httpVerticleDeployment;  // <6>

        }).setHandler(ar -> {   // <7>
            if (ar.succeeded()) {
                startFuture.complete();
            } else {
                startFuture.fail(ar.cause());
            }
        });

    }
}