package com.yangyun.wx;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WeiXinVerticle extends AbstractVerticle{

    public static final Logger LOG = LoggerFactory.getLogger(WeiXinVerticle.class);

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        WebClient client = WebClient.create(vertx);
        EventBus eb=vertx.eventBus();
        MessageConsumer<String> consumer = eb.consumer("api.weixin");
        MessageConsumer<String> consumer2 = eb.consumer("api.alipay");
        MessageConsumer<String> consumer3 = eb.consumer("api.weibo");

        consumer.handler(message -> {
            System.out.println("I have received a message: " + message.body());
            message.reply("how interesting!");
            String action = message.headers().get("action");
            /*
            if(action.equals("getAccessToken")){
                client.get(443, "api.weixin.qq.com", "/cgi-bin/token?grant_type=client_credential&appid=wx62d9ceef6315bb7c&secret=650b44497d17f3576309006bfc41e42e")
                        .ssl(true)
                        .send(ar -> {
                            if (ar.succeeded()) {
                                // Obtain response
                                HttpResponse<Buffer> response = ar.result();
                                message.reply(response.body());

                            } else {
                                System.out.println("Something went wrong " + ar.cause().getMessage());
                            }
                        });
            }*/

        });
        super.start(startFuture);

    }
}