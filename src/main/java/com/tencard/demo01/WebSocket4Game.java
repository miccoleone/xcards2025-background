package com.tencard.demo01;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

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

    // 存储游戏记录
    private static Map<String, List<GameState>> playerGameRecords = new ConcurrentHashMap<>();

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

    @OnMessage
    public void onMessage(String message, Session session) {
        log.info("/game - 收到消息：{}", message);
        
        try {
            UserVO userVO = JSON.parseObject(message, UserVO.class);
            if (userVO.deviceId == null || userVO.card == null) return;

            Long roomId = deviceId2RoomIdMap.get(userVO.deviceId);
            if (roomId == null) return;

            GameState gameState = roomStataMap.computeIfAbsent(roomId, k -> new GameState());
            
            // 如果游戏已经结束，不再接受出牌
            if (gameState.isGameCompleted()) {
                return;
            }

            gameState.addCard(userVO.role, userVO.card);

            if (gameState.isCurrentRoundComplete()) {
                // 发送回合完成消息
                List<UserVO> players = roomId2UserListMap.get(roomId);
                for (UserVO player : players) {
                    Session playerSession = deviceId2SessionMap_Game.get(player.deviceId);
                    if (playerSession != null) {
                        JSONObject roundInfo = new JSONObject();
                        roundInfo.put("type", GameState.MSG_TYPE_ROUND_COMPLETE);
                        roundInfo.put("round", gameState.getRoundNumber());
                        
                        if ("redSide".equals(player.role)) {
                            roundInfo.put("myCard", gameState.getCurrentRedCard());
                            roundInfo.put("oppCard", gameState.getCurrentBlueCard());
                        } else {
                            roundInfo.put("myCard", gameState.getCurrentBlueCard());
                            roundInfo.put("oppCard", gameState.getCurrentRedCard());
                        }
                        
                        sendMessage(playerSession, roundInfo.toJSONString());
                    }
                }

                // 判断回合结果
                String result = gameState.determineRoundResult();
                if (GameState.RESULT_CONTINUE.equals(result)) {
                    gameState.nextRound();  // 进入下一回合
                } else {
                    // 记录游戏结果
                    if (GameState.RESULT_RED_WIN.equals(result)) {
                        UserVO redPlayer = players.stream()
                                .filter(p -> "redSide".equals(p.role))
                                .findFirst()
                                .orElse(null);
                        UserVO bluePlayer = players.stream()
                                .filter(p -> "blueSide".equals(p.role))
                                .findFirst()
                                .orElse(null);
                        if (redPlayer != null && bluePlayer != null) {
                            recordGame(roomId, redPlayer.deviceId, bluePlayer.deviceId);
                        }
                    } else if (GameState.RESULT_BLUE_WIN.equals(result)) {
                        UserVO redPlayer = players.stream()
                                .filter(p -> "redSide".equals(p.role))
                                .findFirst()
                                .orElse(null);
                        UserVO bluePlayer = players.stream()
                                .filter(p -> "blueSide".equals(p.role))
                                .findFirst()
                                .orElse(null);
                        if (redPlayer != null && bluePlayer != null) {
                            recordGame(roomId, bluePlayer.deviceId, redPlayer.deviceId);
                        }
                    }
                    // 平局不记录

                    // 发送游戏结果消息
                    JSONObject resultInfo = new JSONObject();
                    resultInfo.put("type", GameState.MSG_TYPE_GAME_RESULT);
                    resultInfo.put("result", result);
                    broadcastToRoom(roomId, resultInfo.toJSONString());
                }
            } else {
                // 只有一方出牌，通知对手出牌
                List<UserVO> players = roomId2UserListMap.get(roomId);
                UserVO opponent = players.stream()
                        .filter(p -> !p.deviceId.equals(userVO.deviceId))
                        .findFirst()
                        .orElse(null);

                if (opponent != null) {
                    Session opponentSession = deviceId2SessionMap_Game.get(opponent.deviceId);
                    if (opponentSession != null && opponentSession.isOpen()) {
                        JSONObject takeTurnInfo = new JSONObject();
                        takeTurnInfo.put("type", GameState.MSG_TYPE_PLEASE_TAKE_CARD);
                        sendMessage(opponentSession, takeTurnInfo.toJSONString());
                    }
                }
            }
        } catch (Exception e) {
            log.error("/game - Error processing message: ", e);
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

    private void broadcastToRoom(Long roomId, String message) {
        List<UserVO> players = roomId2UserListMap.get(roomId);
        if (players != null) {
            for (UserVO player : players) {
                Session playerSession = deviceId2SessionMap_Game.get(player.deviceId);
                if (playerSession != null && playerSession.isOpen()) {
                    sendMessage(playerSession, message);
                }
            }
        }
    }

    // 记录游戏结果
    private void recordGame(Long roomId, String winner, String loser) {
        GameState currentGame = roomStataMap.get(roomId);
        currentGame.recordGameResult(winner, loser);
        currentGame.setRoomId(roomId);  // 确保设置roomId
        
        // 为双方玩家创建游戏记录
        GameState gameRecord = currentGame.createRecord();
        
        // 为双方添加记录
        playerGameRecords.computeIfAbsent(winner, k -> new CopyOnWriteArrayList<>()).add(gameRecord);
        playerGameRecords.computeIfAbsent(loser, k -> new CopyOnWriteArrayList<>()).add(gameRecord);
        
        // 计算胜利者的连胜次数
        long winStreak = calculateWinStreak(winner, roomId);
        
        // 如果连胜达到3次或以上，生成分享信息
        if (winStreak >= 3) {
            JSONObject shareMessage = new JSONObject();
            shareMessage.put("type", "share");

            // 获取玩家昵称
            String winnerName = roomId2UserListMap.get(roomId).stream()
                    .filter(p -> p.deviceId.equals(winner))
                    .map(p -> p.nickName)
                    .findFirst()
                    .orElse("玩家");
            
            String loserName = roomId2UserListMap.get(roomId).stream()
                    .filter(p -> p.deviceId.equals(loser))
                    .map(p -> p.nickName)
                    .findFirst()
                    .orElse("对手");

            // 根据不同情况设置不同的分享内容
            String shareCode = winStreak >= 5 ? "WIN5" : "WIN3";
            shareMessage.put("shareCode", shareCode);  // 添加shareCode用于前端判断
            shareMessage.put("winnerName", winnerName);  // 添加胜利者昵称
            shareMessage.put("loserName", loserName);    // 添加失败者昵称

            switch (shareCode) {
                case "WIN3":
                    shareMessage.put("title", "连战连捷！");
                    shareMessage.put("content", String.format("%s：恭喜你喜得义子——%s！", winnerName, loserName));
                    break;
                case "WIN5":
                    shareMessage.put("title", "超神！");
                    shareMessage.put("content", String.format("%s5连胜！%s还不拜见义父？", winnerName, loserName));
                    break;
            }

            // 发送分享消息
            Session winnerSession = deviceId2SessionMap_Game.get(winner);
            if (winnerSession != null && winnerSession.isOpen()) {
                sendMessage(winnerSession, shareMessage.toJSONString());
            }
        }
    }

    // 计算指定玩家在当前房间的连胜次数
    private long calculateWinStreak(String playerId, Long roomId) {
        List<GameState> records = playerGameRecords.get(playerId);
        if (records == null) return 0;

        // 使用Stream API计算连胜
        return records.stream()
                .sorted((a, b) -> b.getGameEndTime().compareTo(a.getGameEndTime()))  // 按时间倒序
                .takeWhile(record -> 
                    record.getRoomId().equals(roomId) &&  // 同一房间
                    record.getWinner().equals(playerId))  // 是胜利者
                .count();
    }
}

