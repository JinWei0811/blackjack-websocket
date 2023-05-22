package com.example.websocket;


import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.LoggerFactoryFriend;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;


@Component
@Slf4j
public class WebSocketExample implements WebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketExample.class);

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 當建立連接時觸發，可以在這裡執行相關處理邏輯

    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        logger.debug("Received " + message.getPayload());
        // 收到訊息時觸發，可以在這裡執行相關處理邏輯
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        // 連接出錯時觸發，可以在這裡處理錯誤

    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        // 連接關閉時觸發，可以在這裡執行相關處理邏輯

    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}
