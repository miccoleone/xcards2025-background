package com.tencard.demo01;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.tencard.demo01.GameUtil.*;
import static com.tencard.demo01.WebSocket4Room.*;

/**
 * yue 2021/8/17
 */
@Component
@ServerEndpoint("/game")
@Slf4j
public class WebSocket4Game {

    // roomId -> gameState
    public static Map<Long,GameState> roomStataMap = new ConcurrentHashMap<>(); // 游戏状态 key为roomId

    // 设备id与会话的映射 更新保持最新
    public static Map<String, Session> deviceId2SessionMap_Game = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session) {
        // 从 URL 查询参数获取 deviceId（如果通过URL传递）
        String deviceId = session.getRequestParameterMap().get("deviceId").get(0);

        if (deviceId != null) {
            // 更新session映射
            Session oldSession = deviceId2SessionMap_Game.put(deviceId, session);
            if (oldSession != null && !oldSession.equals(session)) {
                try {
                    oldSession.close();
                } catch (IOException e) {
                    log.error("Error closing old session", e);
                }
            }
            log.info("/game - Device ID: {} connected with session ID: {}", deviceId, session.getId());
        } else {
            log.warn("/game - No deviceId found in the connection request");
        }
    }

    @OnClose
    public void onClose(Session session) {
        String deviceId = findDeviceIdBySession(session);
        if (deviceId != null) {
            // 只有当当前session是最新的session时才移除
            Session currentSession = deviceId2SessionMap_Game.get(deviceId);
            if (session.equals(currentSession)) {
                deviceId2SessionMap_Game.remove(deviceId);
                log.info("/game - Device ID: {} disconnected", deviceId);
            }
        }
    }

    // 根据 session 查找对应的 deviceId
    private String findDeviceIdBySession_Game(Session session) {
        for (Map.Entry<String, Session> entry : deviceId2SessionMap_Game.entrySet()) {
            if (entry.getValue().equals(session)) {
                return entry.getKey();
            }
        }
        return null;
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        User user = JSON.parseObject(message, User.class);
        
        // 如果设备ID或卡牌为空，则直接返回
        if (user.deviceId == null || user.card == null) return;
        
        // 获取对应房间ID
        Long roomId = deviceId2RoomIdMap.get(user.deviceId);
        if (roomId == null) return;
        
        // 获取或创建游戏状态
        GameState gameState = roomStataMap.computeIfAbsent(roomId, k -> new GameState());
        
        // 处理游戏逻辑
        handleGameLogic(user, gameState, roomId, session);
    }

    private void handleGameLogic(User user, GameState gameState, Long roomId, Session session) {
        Integer m = gameState.m;
        Integer otherCard = gameState.otherCard;
        
        Integer card = user.card;
        log.error("/game - m的值为： ".concat(String.valueOf(m)));
        m++;  // 增加回合数
        gameState.m = m;

        if (m % 2 == 0) {  // 双方都出牌了
            // 向房间内的所有玩家发送消息
            roomId2UserListMap.get(roomId).forEach(e -> {
                Session playerSession = deviceId2SessionMap_Game.get(e.deviceId);
                if (playerSession != null) {
                    sendMessage(playerSession, card.toString());
                    sendMessage(playerSession, otherCard.toString());
                }
            });

            // 红色方上点数1-10 蓝色方在下 点数11-20
            int redcard = card;  // 红方卡牌
            int bluecard = otherCard;  // 蓝方卡牌

            // 保证 redcard 小于 bluecard
            if (redcard > bluecard) {
                int temp = redcard;
                redcard = bluecard;
                bluecard = temp;
            }

            // 判断胜负
            if (!(redcard == 1 || bluecard == 11)) {
                if (redcard / (bluecard - TEN) >= 2) {
                    // 红方胜
                    roomId2UserListMap.get(roomId).forEach(e -> {
                        Session playerSession = deviceId2SessionMap_Game.get(e.deviceId);
                        if (playerSession != null) {
                            sendMessage(playerSession, RED_WIN);
                        }
                    });
                } else if ((bluecard - 10) / redcard >= 2) {
                    // 蓝方胜
                    roomId2UserListMap.get(roomId).forEach(e -> {
                        Session playerSession = deviceId2SessionMap_Game.get(e.deviceId);
                        if (playerSession != null) {
                            sendMessage(playerSession, BLUE_WIN);
                        }
                    });
                } else if (gameState.m >= 20) {  // 如果已经是最后一轮
                    // 判断点数大小
                    if (redcard > (bluecard - TEN)) {
                        roomId2UserListMap.get(roomId).forEach(e -> {
                            Session playerSession = deviceId2SessionMap_Game.get(e.deviceId);
                            if (playerSession != null) {
                                sendMessage(playerSession, RED_WIN);
                            }
                        });
                    } else if (redcard < (bluecard - TEN)) {
                        roomId2UserListMap.get(roomId).forEach(e -> {
                            Session playerSession = deviceId2SessionMap_Game.get(e.deviceId);
                            if (playerSession != null) {
                                sendMessage(playerSession, BLUE_WIN);
                            }
                        });
                    } else {
                        // 点数相等，平局
                        roomId2UserListMap.get(roomId).forEach(e -> {
                            Session playerSession = deviceId2SessionMap_Game.get(e.deviceId);
                            if (playerSession != null) {
                                sendMessage(playerSession, "600");  // WIN_WIN
                            }
                        });
                    }
                }
            }
        } else {  // 第一次出牌
            gameState.otherCard = card;
            
            // 提示对方出牌
            roomId2UserListMap.get(roomId)
                    .stream()
                    .filter(e -> !e.deviceId.equals(user.deviceId))
                    .forEach(e -> {
                        Session playerSession = deviceId2SessionMap_Game.get(e.deviceId);
                        if (playerSession != null) {
                            sendMessage(playerSession, PLEASE_TAKE_CARD);
                        }
                    });
        }
    }

    public void sendMessage(Session session,String card) {
            log.info("/game -【websocket消息】广播消息, card={}", card);
            try {
                session.getBasicRemote().sendText(card);
            } catch (Exception e) {
                e.printStackTrace();
            }
    }

    // 根据 session 查找对应的 deviceId
    private String findDeviceIdBySession(Session session) {
        for (Map.Entry<String, Session> entry : deviceId2SessionMap_Game.entrySet()) {
            if (entry.getValue().equals(session)) {
                return entry.getKey();
            }
        }
        return null;
    }

    // 添加重置游戏状态的方法
    public static void resetGameState(Long roomId) {
        GameState gameState = roomStataMap.get(roomId);
        if (gameState != null) {
            gameState.reset();
        }
    }
}
