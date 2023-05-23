package com.example.websocket.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class RoomModel {
    private String roomId;
    /**
     * Waiting
     * Playing
     * Finished
     */
    private String roomState;
    private List<PlayerModel> playerList;
    private List<CardModel> deck;
}
