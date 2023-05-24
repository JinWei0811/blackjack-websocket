package com.example.websocket;

import com.example.websocket.model.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
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
    List<RoomModel> gameRoomList = new ArrayList<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        logger.debug(String.format("%s Room Connection Established", session.getId()));
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        ConnectedMessageModel connected = convertJsonToModel((String) message.getPayload());

        if (connected != null) {
            // 創建新房間
            if (connected.getMethod().equals("create")) {
                var response = createRoom(session, connected);
                TextMessage responseMessage = new TextMessage(convertModelToJsonString(response));
                session.sendMessage(responseMessage);
            }

            // 加入舊房間
            if (connected.getMethod().equals("join") && !connected.getRoomId().isEmpty()) {
                var response = joinRoom(session, connected);
                TextMessage responseMessage = new TextMessage(convertModelToJsonString(response));
                session.sendMessage(responseMessage);
            }

            // 離開房間
            if (connected.getMethod().equals("leave")) {
                var response = leaveRoom(session, connected);
                TextMessage responseMessage = new TextMessage(convertModelToJsonString(response));
                session.sendMessage(responseMessage);
            }

            // 玩家準備就緒
            if (connected.getMethod().equals("ready")) {
                var response = playerReady(session, connected);
                TextMessage responseMessage = new TextMessage(convertModelToJsonString(response));
                session.sendMessage(responseMessage);
                allReadyCheck(connected);
            }

            // 加牌
            if (connected.getMethod().equals("hit")) {
                addCardToHand(session, connected);
            }

            // 不加牌
            if(connected.getMethod().equals("skip")){
                skipCardToHand(session, connected);
            }

        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error(String.format("%s Room Connection Error : %s", session.getId(), exception.getMessage()));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        kickPlayer(session);
        logger.error(String.format("%s Room Connection Closed: %s", session.getId(), closeStatus.getCode()));
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    private ConnectedMessageResponseModel createRoom(WebSocketSession session, ConnectedMessageModel connected) {
        var roomIdExist = false;
        var roomId = "";
        do {
            var tempRoomId = createRoomId();
            if (!roomIds.contains(tempRoomId)) {
                roomId = tempRoomId;
                PlayerModel playerModel = PlayerModel.builder()
                        .sessionId(session.getId())
                        .name(connected.getName())
                        .state("Not Ready")
                        .session(session)
                        .build();

                List<PlayerModel> playerModels = new ArrayList<>();
                playerModels.add(playerModel);

                RoomModel roomModel = RoomModel.builder()
                        .roomId(roomId)
                        .roomState("waiting")
                        .playerList(playerModels)
                        .build();
                gameRoomList.add(roomModel);
                roomIds.add(roomId);
                break;
            }
            roomIdExist = true;
        } while (roomIdExist);

        ConnectedMessageResponseModel connectedMessageResponseModel = ConnectedMessageResponseModel.builder()
                .roomId(roomId)
                .content("創建新房間成功")
                .build();
        return connectedMessageResponseModel;
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

    private ConnectedMessageResponseModel joinRoom(WebSocketSession session, ConnectedMessageModel connected) {
        var participatorCount = 0;
        ConnectedMessageResponseModel connectedMessageResponseModel = ConnectedMessageResponseModel.builder()
                .roomId(connected.getRoomId())
                .build();

        for (var i = 0; i < gameRoomList.size(); i++) {
            if (gameRoomList.get(i).getRoomId().equals(connected.getRoomId())) {
                List<PlayerModel> playerList = gameRoomList.get(i).getPlayerList();
                participatorCount = playerList.size();

                //單間房間最多6人
                if (participatorCount == 6) {
                    connectedMessageResponseModel.setContent(String.format("人數已滿，無法加入 %s 房間", connected.getRoomId()));
                    break;
                }

                // 遊戲進行中
                if (gameRoomList.get(i).getRoomState().equals("playing")) {
                    connectedMessageResponseModel.setContent(String.format("遊戲進行中，無法加入 %s 房間", connected.getRoomId()));
                    break;
                }

                PlayerModel playerModel = PlayerModel.builder()
                        .sessionId(session.getId())
                        .name(connected.getName())
                        .state("Not Ready")
                        .session(session)
                        .build();
                playerList.add(playerModel);

                RoomModel roomModel = gameRoomList.get(i).toBuilder()
                        .playerList(playerList)
                        .build();
                gameRoomList.set(i, roomModel);
                connectedMessageResponseModel.setContent(String.format("成功加入 %s 房間", connected.getRoomId()));
                break;
            }
        }
        return connectedMessageResponseModel;
    }

    private ConnectedMessageResponseModel leaveRoom(WebSocketSession session, ConnectedMessageModel connected) {
        for (var i = 0; i < gameRoomList.size(); i++) {
            if (gameRoomList.get(i).getRoomId().equals(connected.getRoomId())) {
                List<PlayerModel> participators = gameRoomList.get(i).getPlayerList();
                var playerInfo = participators.stream().filter(v -> v.getSessionId().equals(session.getId())).findFirst().orElse(null);
                if (playerInfo != null) {
                    participators.remove(playerInfo);
                    if (gameRoomList.get(i).getPlayerList().size() == 0) {
                        roomIds.remove(gameRoomList.get(i).getRoomId());
                        gameRoomList.remove(gameRoomList.get(i));
                    } else {
                        RoomModel roomModel = gameRoomList.get(i).toBuilder()
                                .playerList(participators)
                                .roomState("waiting")
                                .build();
                        gameRoomList.set(i, roomModel);
                    }
                }
                break;
            }
        }
        ConnectedMessageResponseModel connectedResponse = ConnectedMessageResponseModel.builder()
                .roomId(connected.getRoomId())
                .content(String.format("離開 %s 房間", connected.getRoomId()))
                .build();
        return connectedResponse;
    }

    private void kickPlayer(WebSocketSession session) {
        for (var i = 0; i < gameRoomList.size(); i++) {
            for (var player : gameRoomList.get(i).getPlayerList()) {
                if (player.getSessionId().equals(session.getId())) {
                    var tempPlayerList = gameRoomList.get(i).getPlayerList();
                    tempPlayerList.remove(player);
                    if (gameRoomList.get(i).getPlayerList().size() == 0) {
                        roomIds.remove(gameRoomList.get(i).getRoomId());
                        gameRoomList.remove(gameRoomList.get(i));
                    }
                    return;
                }
            }
        }
    }

    private ConnectedMessageResponseModel playerReady(WebSocketSession session, ConnectedMessageModel connected) {
        for (var room : gameRoomList) {
            if (room.getRoomId().equals(connected.getRoomId())) {
                for (var player : room.getPlayerList()) {
                    if (player.getSessionId().equals(session.getId())) {
                        player.setState("ready");
                        ConnectedMessageResponseModel connectedResponse = ConnectedMessageResponseModel.builder()
                                .roomId(connected.getRoomId())
                                .content(String.format("%s 玩家已準備就緒", connected.getName()))
                                .build();
                        return connectedResponse;
                    }
                }
            }
        }
        ConnectedMessageResponseModel connectedResponse = ConnectedMessageResponseModel.builder()
                .roomId(connected.getRoomId())
                .content(String.format("sessionId: %s 準備失敗", session.getId()))
                .build();
        return connectedResponse;
    }

    private void allReadyCheck(ConnectedMessageModel connected) throws IOException {
        var gameRoom = gameRoomList.stream().filter(v -> v.getRoomId().equals(connected.getRoomId())).findFirst().orElse(null);
        if (gameRoom != null) {
            boolean isAllReady = true;
            for (var player : gameRoom.getPlayerList()) {
                if (!player.getState().equals("ready")) {
                    isAllReady = false;
                }
            }
            if (isAllReady) {
                for (var player : gameRoom.getPlayerList()) {
                    player.getSession().sendMessage(new TextMessage("遊戲開始"));
                }
                gameStart(gameRoom);
            }
        }
    }

    private void gameStart(RoomModel room) throws IOException {
        List<CardModel> deck = createDeck();
        Collections.shuffle(deck);
        room.setDeck(deck);


        // 發第一張牌
        for (PlayerModel player : room.getPlayerList()) {
            List<CardModel> hand = new ArrayList<>();
            CardModel card = room.getDeck().remove(0);
            hand.add(card);
            player.setHand(hand);
            player.setPoint(calculateHandPoints(player.getHand()));
            player.getSession().sendMessage(new TextMessage(convertModelToJsonString(card)));
        }

        // 發第二張牌
        for (PlayerModel player : room.getPlayerList()) {
            CardModel card = room.getDeck().remove(0);
            List<CardModel> playerHand = player.getHand();
            playerHand.add(card);
            player.setHand(playerHand);
            player.setPoint(calculateHandPoints(player.getHand()));
            player.getSession().sendMessage(new TextMessage(convertModelToJsonString(card)));
            if (player.getPoint() == 21) {
                player.getSession().sendMessage(new TextMessage("恭喜！獲得21點。"));
            }
        }
    }

    private void addCardToHand(WebSocketSession session, ConnectedMessageModel connected) throws IOException {
        var room = gameRoomList.stream().filter(v -> v.getRoomId().equals(connected.getRoomId())).findFirst().orElse(null);
        for (var player : room.getPlayerList()) {
            if (player.getSessionId().equals(session.getId())) {
                CardModel card = room.getDeck().remove(0);
                List<CardModel> playerHand = player.getHand();
                playerHand.add(card);
                player.setHand(playerHand);
                player.setPoint(calculateHandPoints(player.getHand()));
                player.getSession().sendMessage(new TextMessage(convertModelToJsonString(card)));
                if (player.getPoint() == 21) {
                    // stop
                    player.getSession().sendMessage(new TextMessage("恭喜！剛好21點"));
                } else if (player.getPoint() > 21) {
                    // bust
                    player.getSession().sendMessage(new TextMessage("不好意思，你輸了"));
                }
            }
        }
    }

    private void skipCardToHand(WebSocketSession session, ConnectedMessageModel connected){
        var room = gameRoomList.stream().filter(v -> v.getRoomId().equals(connected.getRoomId())).findFirst().orElse(null);
        for(var player: room.getPlayerList()){
            if(player.getSessionId().equals(session.getId())){
                player.setState("stay");
            }
        }
    }

    private List<CardModel> createDeck() {
        List<CardModel> deck = new ArrayList<>();
        String[] suits = {"Spades", "Hearts", "Diamonds", "Clubs"};
        String[] ranks = {"2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A"};
        for (String suit : suits) {
            for (String rank : ranks) {
                CardModel card = new CardModel(suit, rank);
                deck.add(card);
            }
        }
        return deck;
    }

    private Integer calculateHandPoints(List<CardModel> hand) {
        int totalPoints = 0;
        int numOfAce = 0;

        for (var card : hand) {
            if (card.getRank().equals("A")) {
                totalPoints += 1;
                numOfAce++;
            } else if (card.getRank().equals("J") || card.getRank().equals("Q") || card.getRank().equals("K")) {
                totalPoints += 10;
            } else {
                totalPoints += Integer.parseInt(card.getRank());
            }
        }

        // 處理A的點數
        for (int i = 0; i < numOfAce; i++) {
            if (totalPoints <= 11) {
                totalPoints += 10;
            }
        }

        return totalPoints;
    }

    private ConnectedMessageModel convertJsonToModel(String jsonPayload) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.readValue(jsonPayload, ConnectedMessageModel.class);
        } catch (IOException exc) {
            logger.error("convertJsonToModel Error" + exc.getMessage(), exc);
            return null;
        }
    }

    private <T> String convertModelToJsonString(T inputModel) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonString = objectMapper.writeValueAsString(inputModel);
        return jsonString;
    }

    public HashSet<String> getRoomIds() {
        return roomIds;
    }

    public List<RoomModel> getGameRoomList() {
        return gameRoomList;
    }
}
