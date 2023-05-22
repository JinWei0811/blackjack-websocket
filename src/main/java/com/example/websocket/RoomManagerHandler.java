package com.example.websocket;

import com.example.websocket.model.GameRoomModel;
import com.example.websocket.model.GameRoomResponseModel;
import com.example.websocket.model.JoinGameModel;
import com.example.websocket.model.PlayerInfoModel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.EnableWebSocket;

import java.io.IOException;
import java.util.*;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class RoomManagerHandler implements WebSocketHandler {
    private static Logger logger = LoggerFactory.getLogger(RoomManagerHandler.class);
    private static final String ALLOWED_CHARACTERS = "abcdefghijkmnpqrstuvwxyzABCDEFGHIJKLMNPQRSTUVWXYZ123456789";
    private static final int ROOM_ID_LENGTH = 6;
    HashSet<String> roomIds = new HashSet<>();
    HashSet<PlayerInfoModel> playerInfoSet = new HashSet<>();
    List<GameRoomModel> gameRoomList = new ArrayList<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        logger.debug("GameRoom Connection Established");
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        JoinGameModel joinInfo = convertJsonToModel((String) message.getPayload());

        if (joinInfo != null) {
            // 創建新房間
            if (joinInfo.getMethod().equals("create")) {
                var response = createRoom(session, joinInfo);
                TextMessage responseMessage = new TextMessage(convertModelToJsonString(response));
                session.sendMessage(responseMessage);
            }

            // 加入舊房間
            if (joinInfo.getMethod().equals("join") && !joinInfo.getRoomId().isEmpty()) {
                var participatorCount = 0;
                for (var i = 0; i < gameRoomList.size(); i++) {
                    if (gameRoomList.get(i).getRoomId().equals(joinInfo.getRoomId())) {
                        List<String> participators = gameRoomList.get(i).getParticipators();
                        participatorCount = participators.size();

                        //單間房間最多6人
                        if (participatorCount == 6) {
                            break;
                        }

                        PlayerInfoModel playerInfoModel = PlayerInfoModel.builder()
                                .sessionId(session.getId())
                                .name(joinInfo.getName())
                                .roomId(joinInfo.getRoomId())
                                .build();
                        playerInfoSet.add(playerInfoModel);

                        participators.add(joinInfo.getName());
                        GameRoomModel gameRoomModel = gameRoomList.get(i).toBuilder()
                                .participators(participators)
                                .build();
                        gameRoomList.set(i, gameRoomModel);
                        break;
                    }
                }

                GameRoomResponseModel gameRoomResponseModel = GameRoomResponseModel.builder()
                        .roomId(joinInfo.getRoomId())
                        .build();

                if (participatorCount == 6) {
                    gameRoomResponseModel.setContent(String.format("人數已滿，無法加入 %s 房間", joinInfo.getRoomId()));
                } else {
                    gameRoomResponseModel.setContent(String.format("成功加入 %s 房間", joinInfo.getRoomId()));
                }
                TextMessage responseMessage = new TextMessage(convertModelToJsonString(gameRoomResponseModel));
                session.sendMessage(responseMessage);
            }

            // 離開房間
            if (joinInfo.getMethod().equals("leave")) {
                for (var i = 0; i < gameRoomList.size(); i++) {
                    if (gameRoomList.get(i).getRoomId().equals(joinInfo.getRoomId())) {
                        List<String> participators = gameRoomList.get(i).getParticipators();
                        participators.remove(joinInfo.getName());

                        GameRoomModel gameRoomModel = gameRoomList.get(i).toBuilder()
                                .participators(participators)
                                .build();
                        gameRoomList.set(i, gameRoomModel);
                        break;
                    }
                }

                GameRoomResponseModel gameRoomResponseModel = GameRoomResponseModel.builder()
                        .roomId(joinInfo.getRoomId())
                        .content(String.format("離開 %s 房間", joinInfo.getRoomId()))
                        .build();
                TextMessage responseMessage = new TextMessage(convertModelToJsonString(gameRoomResponseModel));
                session.sendMessage(responseMessage);
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error("GameRoom Connection Error :" + exception.getMessage());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        logger.error("GameRoom Connection Closed: ", closeStatus.getCode());
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    private GameRoomResponseModel createRoom(WebSocketSession session, JoinGameModel joinInfo) {
        var roomIdExist = true;
        var roomId = "";
        while (roomIdExist) {
            var tempRoomId = createRoomId();
            if (!roomIds.contains(tempRoomId)) {
                roomIdExist = false;
                roomId = tempRoomId;

                List<String> participators = new ArrayList<>();
                participators.add(joinInfo.getName());

                GameRoomModel gameRoomModel = GameRoomModel.builder()
                        .roomId(roomId)
                        .roomState("create")
                        .participators(participators)
                        .build();
                gameRoomList.add(gameRoomModel);
            }
        }
        PlayerInfoModel playerInfoModel = PlayerInfoModel.builder()
                .sessionId(session.getId())
                .name(joinInfo.getName())
                .roomId(roomId)
                .build();
        playerInfoSet.add(playerInfoModel);

        GameRoomResponseModel gameRoomResponseModel = GameRoomResponseModel.builder()
                .roomId(roomId)
                .content("創建新房間成功")
                .build();
        return gameRoomResponseModel;
    }

    private String createRoomId() {
        StringBuilder stringBuilder = new StringBuilder(ROOM_ID_LENGTH);
        Random random = new Random();
        for (int i = 0; i < ROOM_ID_LENGTH; i++) {
            int randomIndex = random.nextInt(ALLOWED_CHARACTERS.length());
            char randomChar = ALLOWED_CHARACTERS.charAt(randomIndex);
            stringBuilder.append(randomChar);
        }
        return stringBuilder.toString();
    }

    private JoinGameModel convertJsonToModel(String jsonPayload) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.readValue(jsonPayload, JoinGameModel.class);
        } catch (IOException exc) {
            logger.error("convertJsonToModel Error" + exc.getMessage(), exc);
            return null;
        }
    }

    private String convertModelToJsonString(GameRoomResponseModel gameRoomResponseModel) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonString = objectMapper.writeValueAsString(gameRoomResponseModel);
        return jsonString;
    }
}
