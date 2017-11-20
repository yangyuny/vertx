package com.yangyun.web;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

public class WebClientVerticle extends AbstractVerticle{

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        WebClient client = WebClient.create(vertx);
        // Send a GET request
        client
                .get(80, "myserver.mycompany.com", "/some-uri")
                .send(ar -> {
                    if (ar.succeeded()) {
                        // Obtain response
                        HttpResponse<Buffer> response = ar.result();

                        System.out.println("Received response with status code" + response.statusCode());
                    } else {
                        System.out.println("Something went wrong " + ar.cause().getMessage());
                    }
                });
    }

}