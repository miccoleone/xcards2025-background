package com.tencard.demo01;

import lombok.Data;

/**
 * 游戏消息封装类，用于前后端传递游戏中的动作和数据。
 */
@Data
public class GameMessage {
    
    private String action;   // 游戏动作，如 "startGame", "playCard"
    private String deviceId; // 玩家设备ID
    private String role;     // 玩家角色，可能是 "blueSide", "redSide"
    private int card;        // 出的牌，可能是牌的编号或者值
    private String roomCode; // 房间编码
    private String roomId;   // 房间ID
    private String nickName; // 玩家昵称
    private int winRate;     // 玩家胜率

    // 可以根据游戏逻辑扩展更多字段，比如：轮次、剩余时间等
}
