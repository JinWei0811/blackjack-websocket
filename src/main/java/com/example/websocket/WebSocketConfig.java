package com.example.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final WebSocketExample webSocketExample;
    private final RoomManagerHandler roomManagerHandler;

    public WebSocketConfig(WebSocketExample webSocketExample,
                           RoomManagerHandler roomManagerHandler) {
        this.webSocketExample = webSocketExample;
        this.roomManagerHandler = roomManagerHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketExample, "/example").setAllowedOrigins("*");
        registry.addHandler(roomManagerHandler, "/connect").setAllowedOrigins("*");
    }
}

