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
     * Ready
     * Not Ready
     * Waiting
     * Playing
     * Finished
     * Leave
     */
    private String state;
    private BigDecimal chip;
    private List<CardModel> hand;
    private Integer point;
}
