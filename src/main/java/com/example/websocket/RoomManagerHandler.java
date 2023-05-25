package com.example.websocket;

import com.example.websocket.model.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.EnableWebSocket;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class RoomManagerHandler implements WebSocketHandler {
    private static Logger logger = LoggerFactory.getLogger(RoomManagerHandler.class);
    private static final String ALLOWED_CHARACTERS = "abcdefghijkmnpqrstuvwxyzABCDEFGHIJKLMNPQRSTUVWXYZ123456789";
    private static final int ROOM_ID_LENGTH = 6;
    private static final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    HashSet<String> roomIds = new HashSet<>();
    List<RoomModel> gameRoomList = new ArrayList<>();

    // Room State
    private static final String CREATE = "create";
    private static final String JOIN = "join";
    private static final String WAITING = "waiting";
    private static final String PLAYING = "playing";
    private static final String LEAVE = "leave";

    // Player State
    private static final String READY = "ready";
    private static final String NOT_READY = "not ready";
    private static final String CONTINUE = "continue";
    private static final String HIT = "hit";
    private static final String SKIP = "skip";
    private static final String BUST = "bust";
    private static final String WIN = "win";
    private static final String LOSE = "lose";


    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        logger.debug(String.format("%s Room Connection Established", session.getId()));
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        logger.debug((String) message.getPayload());
        ConnectedMessageModel connected = convertJsonToModel((String) message.getPayload());

        if (connected != null) {
            // 重新連接
            if (connected.getMethod().equals("reconnect")) {
                var response = reconnect(session, connected);
                TextMessage responseMessage = new TextMessage(convertModelToJsonString(response));
                session.sendMessage(responseMessage);
            }

            // 創建新房間
            if (connected.getMethod().equals(CREATE)) {
                var response = createRoom(session, connected);
                RoomModel roomModel = findRoomInfoByRoomId(response.getRoomId());
                if (roomModel != null) {
                    response.setPlayerList(playerNameList(roomModel.getPlayerList()));
                    response.setPlayerStateList(playerStateList(roomModel.getPlayerList()));
                    response.setSessionId(session.getId());
                    TextMessage responseMessage = new TextMessage(convertModelToJsonString(response));
                    session.sendMessage(responseMessage);
                }
            }

            // 加入舊房間
            if (connected.getMethod().equals(JOIN) && !connected.getRoomId().isEmpty()) {
                var response = joinRoom(session, connected);
                RoomModel roomModel = findRoomInfoByRoomId(connected.getRoomId());
                if (roomModel != null) {
                    List<PlayerModel> playerList = roomModel.getPlayerList();
                    response.setPlayerList(playerNameList(playerList));
                    response.setPlayerStateList(playerStateList(roomModel.getPlayerList()));
                    response.setSessionId(session.getId());
                    brodcastToPlayers(convertModelToJsonString(response), playerList);
                }
            }

            // 離開房間
            if (connected.getMethod().equals(LEAVE)) {
                var response = leaveRoom(session, connected);
                RoomModel roomModel = findRoomInfoByRoomId(connected.getRoomId());
                if (roomModel != null) {
                    List<PlayerModel> playerList = roomModel.getPlayerList();
                    response.setPlayerList(playerNameList(playerList));
                    response.setPlayerStateList(playerStateList(roomModel.getPlayerList()));
                    brodcastToPlayers(convertModelToJsonString(response), playerList);
                }
            }

            // 玩家準備就緒
            if (connected.getMethod().equals(READY)) {
                var response = playerReady(session, connected);
                RoomModel roomModel = findRoomInfoByRoomId(connected.getRoomId());
                if (roomModel != null) {
                    List<PlayerModel> playerList = roomModel.getPlayerList();
                    response.setPlayerList(playerNameList(playerList));
                    response.setPlayerStateList(playerStateList(roomModel.getPlayerList()));
                    brodcastToPlayers(convertModelToJsonString(response), playerList);
                    allReadyCheck(connected);
                }
            }

            // 玩家取消準備
            if (connected.getMethod().equals(NOT_READY)) {
                var response = playerNotReady(session, connected);
                RoomModel roomModel = findRoomInfoByRoomId(connected.getRoomId());
                if (roomModel != null) {
                    List<PlayerModel> playerList = roomModel.getPlayerList();
                    response.setPlayerList(playerNameList(playerList));
                    response.setPlayerStateList(playerStateList(roomModel.getPlayerList()));
                    brodcastToPlayers(convertModelToJsonString(response), playerList);
                }
            }

            // 加牌
            if (connected.getMethod().equals(HIT)) {
                addCardToHand(session, connected);
                allStayCheck(connected.getRoomId());
            }

            // 不加牌
            if (connected.getMethod().equals(SKIP)) {
                skipCardToHand(session, connected);
                allStayCheck(connected.getRoomId());
            }

        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error(String.format("%s Room Connection Error : %s", session.getId(), exception.getMessage()));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
//        kickPlayer(session);
        logger.error(String.format("%s Room Connection Closed: %s", session.getId(), closeStatus.getCode()));
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    private ConnectedMessageResponseModel reconnect(WebSocketSession session, ConnectedMessageModel connected) {
        RoomModel room = gameRoomList.stream().filter(v -> v.getRoomId().equals(connected.getRoomId())).findFirst().orElse(null);
        ConnectedMessageResponseModel connectedResponse = ConnectedMessageResponseModel.builder()
                .roomId(connected.getRoomId())
                .build();
        if (room != null) {
            PlayerModel player = room.getPlayerList().stream().filter(v -> v.getSessionId().equals(connected.getSessionId())).findFirst().orElse(null);
            if (player != null) {
                player.setSession(session);
                player.setSessionId(session.getId());

                connectedResponse.setSessionId(session.getId());
                connectedResponse.setPlayerList(playerNameList(room.getPlayerList()));
                connectedResponse.setPlayerStateList(playerStateList(room.getPlayerList()));
                connectedResponse.setContent("reconnect success");
            }
        }
        return connectedResponse;
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
                        .state(NOT_READY)
                        .session(session)
                        .build();
                PlayerModel botPlayer = PlayerModel.builder()
                        .sessionId("bot")
                        .name("bot")
                        .state(READY)
                        .build();

                List<PlayerModel> playerModels = new ArrayList<>();
                playerModels.add(playerModel);
                playerModels.add(botPlayer);

                RoomModel roomModel = RoomModel.builder()
                        .roomId(roomId)
                        .roomState(WAITING)
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
                if (gameRoomList.get(i).getRoomState().equals(PLAYING)) {
                    connectedMessageResponseModel.setContent(String.format("遊戲進行中，無法加入 %s 房間", connected.getRoomId()));
                    break;
                }

                PlayerModel playerModel = PlayerModel.builder()
                        .sessionId(session.getId())
                        .name(connected.getName())
                        .state(NOT_READY)
                        .session(session)
                        .build();
                playerList.add(playerModel);

                RoomModel roomModel = gameRoomList.get(i).toBuilder()
                        .playerList(playerList)
                        .build();
                gameRoomList.set(i, roomModel);
                connectedMessageResponseModel.setContent(String.format("%s 成功加入房間", connected.getName(), connected.getRoomId()));
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
                                .roomState(WAITING)
                                .build();
                        gameRoomList.set(i, roomModel);
                    }
                }
                break;
            }
        }
        ConnectedMessageResponseModel connectedResponse = ConnectedMessageResponseModel.builder()
                .roomId(connected.getRoomId())
                .content(String.format("%s 離開房間", connected.getName(), connected.getRoomId()))
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
                    if (player.getName().equals(connected.getName())) {
                        player.setState(READY);
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

    private ConnectedMessageResponseModel playerNotReady(WebSocketSession session, ConnectedMessageModel connected) {
        RoomModel room = findRoomInfoByRoomId(connected.getRoomId());
        if (room != null) {
            PlayerModel player = room.getPlayerList().stream().filter(v -> v.getSessionId().equals(session.getId())).findFirst().orElse(null);
            if (player != null) {
                player.setState(NOT_READY);
                ConnectedMessageResponseModel connectedResponse = ConnectedMessageResponseModel.builder()
                        .roomId(connected.getRoomId())
                        .content(String.format("%s 玩家取消準備", connected.getName()))
                        .build();
                return connectedResponse;
            }
        }
        ConnectedMessageResponseModel connectedResponse = ConnectedMessageResponseModel.builder()
                .roomId(connected.getRoomId())
                .content(String.format("%s 玩家取消準備失敗", connected.getName()))
                .build();
        return connectedResponse;
    }

    private void allReadyCheck(ConnectedMessageModel connected) throws IOException, InterruptedException {
        Thread thread = new Thread(() -> {
            var gameRoom = findRoomInfoByRoomId(connected.getRoomId());
            if (gameRoom != null) {
                boolean isAllReadyFirstCheck = gameRoom.getPlayerList().stream().filter(v -> v.getState().equals(NOT_READY)).findFirst().orElse(null) == null ? true : false;
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                boolean isAllReadySecondCheck = gameRoom.getPlayerList().stream().filter(v -> v.getState().equals(NOT_READY)).findFirst().orElse(null) == null ? true : false;

                if (isAllReadyFirstCheck && isAllReadySecondCheck) {
                    for (var player : gameRoom.getPlayerList()) {
                        player.setState(CONTINUE);
                    }

                    ConnectedMessageResponseModel connectedMessageResponseModel = ConnectedMessageResponseModel.builder()
                            .roomId(connected.getRoomId())
                            .playerList(playerNameList(gameRoom.getPlayerList()))
                            .playerStateList(playerStateList(gameRoom.getPlayerList()))
                            .content("遊戲開始")
                            .build();
                    gameRoom.setRoomState(PLAYING);
                    try {
                        brodcastToPlayers(convertModelToJsonString(connectedMessageResponseModel), gameRoom.getPlayerList());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    try {
                        gameStart(gameRoom);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        });
        thread.start();
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
            brodcastToPlayers(convertModelToJsonString(getPlayerCard(player, CONTINUE)), room.getPlayerList());
        }

        // 發第二張牌
        for (PlayerModel player : room.getPlayerList()) {
            CardModel card = room.getDeck().remove(0);
            List<CardModel> playerHand = player.getHand();
            playerHand.add(card);
            player.setHand(playerHand);
            player.setPoint(calculateHandPoints(player.getHand()));
            player.setState(calculateHandBrodcast(player, room.getPlayerList(), room.getRoomId()));
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
                player.setState(calculateHandBrodcast(player, room.getPlayerList(), connected.getRoomId()));
            }
        }
    }

    private void skipCardToHand(WebSocketSession session, ConnectedMessageModel connected) throws IOException {
        var room = gameRoomList.stream().filter(v -> v.getRoomId().equals(connected.getRoomId())).findFirst().orElse(null);
        for (var player : room.getPlayerList()) {
            if (player.getSessionId().equals(session.getId())) {
                player.setState(SKIP);
                brodcastToPlayers(convertModelToJsonString(getPlayerCard(player, SKIP)), room.getPlayerList());
            }
        }
    }

    private void allStayCheck(String roomId) throws IOException {
        RoomModel room = findRoomInfoByRoomId(roomId);
        Boolean isAllStay = false;
        List<PlayerModel> playerList = room.getPlayerList();
        for (var player : playerList) {
            if (player.getSessionId().equals("bot")) {
                continue;
            }
            isAllStay = player.getState().equals(CONTINUE) ? false : true;
            if (!isAllStay) {
                break;
            }
        }
        if (isAllStay) {
            PlayerModel botPlayer = room.getPlayerList().stream().filter(v -> v.getSessionId().equals("bot")).findFirst().orElse(null);
            if (botPlayer != null) {
                while (botPlayer.getPoint() <= 16) {
                    CardModel card = room.getDeck().remove(0);
                    List<CardModel> botHand = botPlayer.getHand();
                    botHand.add(card);
                    botPlayer.setHand(botHand);
                    botPlayer.setPoint(calculateHandPoints(botPlayer.getHand()));
                    botPlayer.setState(calculateHandBrodcast(botPlayer, playerList, roomId));
                }

                if (botPlayer.getState().equals(CONTINUE) && botPlayer.getPoint() > 16) {
                    botPlayer.setState(SKIP);
                }

                if (botPlayer.getState().equals(SKIP)) {
                    for (var player : playerList) {
                        if (player.getSessionId().equals("bot")) {
                            continue;
                        }
                        if (player.getPoint() <= botPlayer.getPoint() || player.getState().equals(BUST)) {
                            GameResultModel gameResult = GameResultModel.builder()
                                    .roomId(roomId)
                                    .name(player.getName())
                                    .result(LOSE)
                                    .build();
                            brodcastToPlayers(convertModelToJsonString(gameResult), playerList);
                        } else {
                            GameResultModel gameResult = GameResultModel.builder()
                                    .roomId(roomId)
                                    .name(player.getName())
                                    .result(WIN)
                                    .build();
                            brodcastToPlayers(convertModelToJsonString(gameResult), playerList);
                        }
                        player.setState(NOT_READY);
                    }
                }

                if (botPlayer.getState().equals(BUST)) {
                    for (var player : playerList) {
                        GameResultModel gameResult = GameResultModel.builder()
                                .roomId(roomId)
                                .name(player.getName())
                                .result(WIN)
                                .build();
                        brodcastToPlayers(convertModelToJsonString(gameResult), playerList);
                    }
                }
                List<Integer> chips = new ArrayList<>();
                List<String> name = new ArrayList<>();
                for (var player : room.getPlayerList()) {
                    chips.add(player.getChip());
                    name.add(player.getName());
                }
                RoomPlayerModel roomPlayer = RoomPlayerModel.builder()
                        .names(name)
                        .chips(chips)
                        .build();
                brodcastToPlayers(convertModelToJsonString(roomPlayer), room.getPlayerList());
            }
        }
    }

    private String calculateHandBrodcast(PlayerModel player, List<PlayerModel> playerList, String roomId) throws IOException {
        String state = "";
        if (player.getPoint() == 21) {
            state = SKIP;
            if (!player.getSessionId().equals("bot")) {
                player.getSession().sendMessage(new TextMessage("恭喜！獲得21點"));
                allStayCheck(roomId);
            }
            brodcastToPlayers(convertModelToJsonString(getPlayerCard(player, state)), playerList);
        } else if (player.getPoint() > 21) {
            state = BUST;
            if (!player.getSessionId().equals("bot")) {
                player.getSession().sendMessage(new TextMessage("不好意思，你輸了"));
                allStayCheck(roomId);
            }
            brodcastToPlayers(convertModelToJsonString(getPlayerCard(player, state)), playerList);
        } else {
            state = CONTINUE;
            brodcastToPlayers(convertModelToJsonString(getPlayerCard(player, state)), playerList);
        }
        return state;
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
//            logger.error("convertJsonToModel Error" + exc.getMessage(), exc);
            return null;
        }
    }

    private <T> String convertModelToJsonString(T inputModel) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        String jsonString = objectMapper.writeValueAsString(inputModel);
        return jsonString;
    }

    private void brodcastToPlayers(String text, List<PlayerModel> playerList) throws IOException {
        TextMessage textMessage = new TextMessage(text);
        for (var player : playerList) {
            if (player.getSessionId().equals("bot")) {
                continue;
            }
            player.getSession().sendMessage(textMessage);
        }
    }

    private List<String> playerNameList(List<PlayerModel> playerList) {
        List<String> nameList = new ArrayList<>();
        for (var player : playerList) {
            nameList.add(player.getName());
        }
        return nameList;
    }

    private List<String> playerStateList(List<PlayerModel> playerList) {
        List<String> stateList = new ArrayList<>();
        for (var player : playerList) {
            stateList.add(player.getState());
        }
        return stateList;
    }

    private RoomModel findRoomInfoByRoomId(String roomId) {
        return gameRoomList.stream().filter(v -> v.getRoomId().equals(roomId)).findFirst().orElse(null);
    }

    private PlayerCardModel getPlayerCard(PlayerModel player, String state) {
        List<String> suits = new ArrayList<>();
        List<String> ranks = new ArrayList<>();
        for (var card : player.getHand()) {
            suits.add(card.getSuits());
            ranks.add(card.getRank());
        }

        return PlayerCardModel.builder()
                .name(player.getName())
                .suits(suits)
                .ranks(ranks)
                .state(state)
                .points(calculateHandPoints(player.getHand()))
                .build();
    }
}
