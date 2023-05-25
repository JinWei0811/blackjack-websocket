package com.example.websocket.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.socket.WebSocketSession;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class PlayerModel {
    private String sessionId;
    private String name;
    private WebSocketSession session;
    /**
     * ready
     * not ready
     * waiting
     * playing
     * stay
     * bust
     * finished
     * leave
     */
    private String state;
    private Integer chip;
    private List<CardModel> hand;
    private Integer point;
}
