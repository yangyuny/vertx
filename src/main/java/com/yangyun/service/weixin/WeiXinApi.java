
package com.yangyun.service.weixin;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.core.Vertx;

@ProxyGen
public interface WeiXinApi {

    @Fluent
    WeiXinApi getAccessToken();

    static WeiXinApi create(Vertx vertx){
        return null;
    }

    static WeiXinApi createProxy(Vertx vertx,String address){
        return null;
    }
}
