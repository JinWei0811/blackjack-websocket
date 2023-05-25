package com.example.websocket.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConnectedMessageResponseModel {
    private String roomId;
    private String sessionId;
    private List<String> playerList;
    private List<String> playerStateList;
    private String content;
}
