package com.tencard.demo01;

import lombok.Data;

import javax.websocket.Session;

@Data
public class User {
    private static final long serialVersionUID = 1L;
    
    public String deviceId;
    

    public String role; // blueSide|redSide
    

    public String roomCode;// 房间号 4396
    

    public Long roomId; // 房间Id（后台逻辑）10000
    

    public Integer card; // 本轮出牌
    

    public String nickName;
    

    public Long userCode; // 用户号（后台逻辑）10000
    
    public String sessionId; //
    
    public transient Session session;
    
    public String openid;
    
    public Integer winRate; // 胜率 49
    
    public String msgCode; // 消息编号 50 60 100 200

}


