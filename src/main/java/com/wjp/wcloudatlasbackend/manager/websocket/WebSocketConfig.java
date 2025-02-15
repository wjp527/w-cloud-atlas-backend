package com.wjp.wcloudatlasbackend.manager.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import javax.annotation.Resource;

/**
 * WebSocket配置 (定义连接)
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    /**
     * 注入WebSocket处理器
      */
    @Resource
    private PictureEditHandler pictureEditHandler;

    /**
     * 注入WebSocket拦截器
     */
    @Resource
    private WsHandshakeInterceptor wsHandshakeInterceptor;

    /**
     * 注册WebSocket处理器
     * @param registry
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(pictureEditHandler, "/ws/picture/edit")
                // 添加拦截器
                .addInterceptors(wsHandshakeInterceptor)
                // 设置允许跨域访问
                .setAllowedOrigins("*");

    }
}
