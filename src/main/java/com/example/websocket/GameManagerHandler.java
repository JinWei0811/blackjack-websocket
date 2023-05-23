package com.example.websocket;

import com.example.websocket.model.GameMessageModel;
import com.example.websocket.model.RoomModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;

import java.io.IOException;
import java.util.List;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class GameManagerHandler implements WebSocketHandler {
    private static Logger logger = LoggerFactory.getLogger(RoomManagerHandler.class);
    private RoomManagerHandler roomManagerHandler;

    @Autowired
    public GameManagerHandler(RoomManagerHandler roomManagerHandler) {
        this.roomManagerHandler = roomManagerHandler;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        logger.debug(String.format("%s Connected", session.getId()));
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        GameMessageModel gameMessage = convertJsonToGameMessage((String) message.getPayload());

        if (gameMessage != null) {
            if (gameMessage.getMethod().equals("start")) {
                List<RoomModel> gameRoomList = roomManagerHandler.getGameRoomList();
                for (var gameRoom : gameRoomList) {
                    if (gameRoom.getRoomId().equals(gameMessage.getRoomId())) {

                    }
                }
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.debug(String.format("%s Connected Error %s", session.getId(), exception.getMessage()));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        logger.error(String.format("%s Room Connection Closed: %s", session.getId(), closeStatus.getCode()));
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    private GameMessageModel convertJsonToGameMessage(String jsonPayload) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.readValue(jsonPayload, GameMessageModel.class);
        } catch (IOException exc) {
            logger.error("convertJsonToGameMessage Error" + exc.getMessage(), exc);
            return null;
        }
    }
}
