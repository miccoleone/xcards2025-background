package com.tencard.demo01;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;

import javax.websocket.Session;

@Slf4j
public class GameUtil {

    public static final int TEN = 10;

    // 蓝色方进入游戏 50
    public static final String BlUE_JOIN_GAME = "50";
    // 红色方进入游戏 60
    public static final String RED_JOIN_GAME = "60";
    // 红方胜出
    public static final String RED_WIN = "100";
    // 蓝方胜出
    public static final String BLUE_WIN = "200";
    // 请出牌
    public static final String PLEASE_TAKE_CARD = "300";
    // 平局
    public static final String WIN_WIN = "600";
//    // 隐藏请出牌三个字
//    public static final String HIDDEN_PLEASE_TAKE_CARD = "400";

    // roomId
    private static Long currentRomeId = 10000L;

    // 获取下一个递增的房间 Id
    public static synchronized Long getNextRomeId() {
        return currentRomeId++;
    }

    // userCode
    private static Long currentUserCode = 10000L;

    // 获取下一个递增的用户code
    public static synchronized Long getNextUserCode() {
        return currentUserCode++;
    }

    public static enum RoleEnum{
        blueSide,redSide
    }

    // 发送消息给玩家
    public static void sendMessage(Session session, Object message) {
        Gson gson = new Gson();

        String jsonMessage = gson.toJson(message);
        try {
            if (session != null && session.isOpen()) {
                log.info("************** GameUtil 发送消息 sessionid为 ：{} || 消息为：{} ************** ", session.getId(),jsonMessage);
                session.getBasicRemote().sendText(jsonMessage);
            }
        } catch (Exception e) {
            log.error("/GameUtil sendMessage error!:", e);
        }
    }
}
