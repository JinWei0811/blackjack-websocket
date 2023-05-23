package com.example.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
@Slf4j
public class QueryHandler implements WebSocketHandler {
    private static Logger logger = LoggerFactory.getLogger(RoomManagerHandler.class);
    private RoomManagerHandler roomManagerHandler;

    @Autowired
    public QueryHandler(RoomManagerHandler roomManagerHandler) {
        this.roomManagerHandler = roomManagerHandler;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {

    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        var temp = this.roomManagerHandler.getGameRoomList();
        var temp1 = this.roomManagerHandler.getRoomIds();
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {

    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {

    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}
