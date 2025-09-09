package com.tencard.demo01;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
@ServerEndpoint("/match")
@Slf4j
public class WebSocket4Match {

    // 设备ID -> 会话映射
    private static final Map<String, Session> deviceId2SessionMap = new ConcurrentHashMap<>();
    // 房间ID -> Room 对象映射
    private static final Map<Long, Room> rooms = new ConcurrentHashMap<>();
    // 设备ID -> 房间ID 映射
    private static final Map<String, Long> deviceId2RoomIdMap = new ConcurrentHashMap<>();
    // 房间Code -> 房间ID 映射
    private static final Map<String, Long> roomCode2RoomIdMap = new ConcurrentHashMap<>();
    // 玩家游戏记录：设备ID -> List<GameState>
    private static final Map<String, List<GameState>> playerGameRecords = new ConcurrentHashMap<>();
    // 正在处理离开房间的设备ID集合，防止重复处理
    private static final Set<String> leavingDeviceIds = ConcurrentHashMap.newKeySet();

    @OnOpen
    public void onOpen(Session session) {
        String deviceId = session.getRequestParameterMap().get("deviceId").get(0);
        log.info("/match - Device ID: {} connected with session ID: {}", deviceId, session.getId());
        if (deviceId == null) {
            log.warn("/match - No deviceId found in the connection request");
            return;
        }
        // 清洗 deviceId，去掉可能的查询参数格式
        deviceId = cleanDeviceId(deviceId);
        Session oldSession = deviceId2SessionMap.put(deviceId, session);
        if (oldSession != null && !oldSession.equals(session)) {
            try {
                oldSession.close();
            } catch (IOException e) {
                log.error("Error closing old session for deviceId: {}", deviceId, e);
            }
        }
        log.info("/match - Device ID: {} connected with session ID: {}", deviceId, session.getId());
    }

    @OnClose
    public void onClose(Session session) {
        String deviceId = findDeviceIdBySession(session);
        if (deviceId == null) {
            log.warn("/match - No deviceId found in the close request");
            return;
        }
        Session currentSession = deviceId2SessionMap.get(deviceId);
        if (session.equals(currentSession)) {
            log.info("/match - Device ID: {} disconnected", deviceId);
            
            // 只处理连接层清理，不直接调用handleLeaveRoom
            // 这样可以避免正常的连接断开（如页面刷新）触发不必要的业务逻辑
            deviceId2SessionMap.remove(deviceId);
            
            // 检查是否真的需要处理离开房间逻辑
            Long roomId = deviceId2RoomIdMap.get(deviceId);
            if (roomId != null) {
                Room room = rooms.get(roomId);
                if (room != null && room.getPlayers().size() == 2) {
                    // 只在游戏进行中或等待状态下才通知对方离开
                    // 避免在正常游戏流程中的连接波动造成误报
                    handlePlayerDisconnect(deviceId);
                } else {
                    // 单人房间或房间不存在，直接清理
                    cleanupPlayerData(deviceId);
                }
            }
        }
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        log.error("/match - WebSocket error", throwable);
        String deviceId = findDeviceIdBySession(session);
        if (deviceId != null) {
            deviceId2SessionMap.remove(deviceId);
            handleLeaveRoom(deviceId);
        }
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        String deviceId = findDeviceIdBySession(session);
        if (deviceId == null) {
            log.warn("/match - No deviceId found in the message request");
            return;
        }
        log.info("/match - 收到消息：{}", message);
        try {
            JSONObject jsonMessage = JSON.parseObject(message);
            String type = jsonMessage.getString("type");

            switch (type) {
                case "join_room":
                    PlayerVO user = JSON.parseObject(message, PlayerVO.class);
                    if (user.getDeviceId() == null || user.getRoomCode() == null) {
                        log.error("/match - Invalid join_room message: deviceId or roomCode is null");
                        return;
                    }
                    user.setSession(session);
                    user.setSessionId(session.getId());
                    user.setDeviceId(cleanDeviceId(user.getDeviceId())); // 清洗 deviceId
                    handleJoinRoom(user);
                    break;
                case "play_card":
                    if (jsonMessage.getString("deviceId") == null || jsonMessage.getInteger("card") == null) {
                        log.error("/match - Invalid play_card message: deviceId or card is null");
                        return;
                    }
                    handlePlayCard(cleanDeviceId(jsonMessage.getString("deviceId")), jsonMessage.getInteger("card"));
                    break;
                case "leave_room":
                    handleLeaveRoom(deviceId);
                    break;
                case "rematch_request":
                    handleRematchRequest(deviceId);
                    break;
                case "rematch_accept":
                    handleRematchAccept(deviceId);
                    break;
                case "rematch_reject":
                    handleRematchReject(deviceId);
                    break;
                default:
                    log.warn("/match - Unknown message type: {}", type);
            }
        } catch (Exception e) {
            log.error("/match - Error processing message: {}", message, e);
        }
    }

    private String findDeviceIdBySession(Session session) {
        for (Map.Entry<String, Session> entry : deviceId2SessionMap.entrySet()) {
            if (entry.getValue().equals(session)) {
                log.debug("/match - Found deviceId: {} for session: {}", entry.getKey(), session.getId());
                return entry.getKey();
            }
        }
        log.warn("/match - No deviceId found for session: {}", session.getId());
        return null;
    }

    // 清洗 deviceId，去掉可能的查询参数格式
    private String cleanDeviceId(String deviceId) {
        if (deviceId != null && deviceId.contains("?")) {
            String[] parts = deviceId.split("\\?deviceId=");
            if (parts.length > 0) {
                deviceId = parts[0];
            }
        }
        return deviceId;
    }

    private void handleJoinRoom(PlayerVO user) {
        String deviceId = user.getDeviceId();
        String roomCode = user.getRoomCode();
        String nickname = user.getNickName();
        
        // 🔥 昵称内容安全检查
        log.info("🔥 开始检查昵称: {}", nickname);
        if (nickname == null || nickname.trim().isEmpty()) {
            log.warn("🔥 昵称为空，拒绝加入房间");
            sendErrorMessage(user.getSession(), "昵称不能为空");
            return;
        }
        
        // 简单的敏感词检查
        String[] sensitiveWords = {"系统", "管理员", "admin", "fuck", "shit", "政府"};
        String lowerNickname = nickname.toLowerCase();
        for (String word : sensitiveWords) {
            if (lowerNickname.contains(word.toLowerCase())) {
                log.warn("🔥 昵称包含敏感词，拒绝加入房间: {}", nickname);
                sendErrorMessage(user.getSession(), "昵称包含敏感词，请重新输入");
                return;
            }
        }
        
        log.info("🔥 昵称检查通过: {}", nickname);

        user.setWinRate(49);
        user.setUserCode(GameUtil.getNextUserCode());

        if (roomCode2RoomIdMap.containsKey(roomCode)) {
            Long roomId = roomCode2RoomIdMap.get(roomCode);
            Room room = rooms.get(roomId);
            if (room == null || room.getPlayers().size() >= 2) {
                PlayerVO response = new PlayerVO();
                response.setType("room_full");
                GameUtil.sendMessage(user.getSession(), response);
                return;
            }

            boolean isReconnect = room.getPlayers().stream()
                    .anyMatch(p -> p.getDeviceId().equals(deviceId));
            if (!isReconnect) {
                user.setRole(GameUtil.RoleEnum.redSide.toString());
                user.setMsgCode(GameUtil.RED_JOIN_GAME);
                room.addPlayer(user);
            } else {
                room.getPlayers().stream()
                        .filter(p -> p.getDeviceId().equals(deviceId))
                        .forEach(p -> {
                            p.setSession(user.getSession());
                            p.setSessionId(user.getSessionId());
                        });
            }
            deviceId2RoomIdMap.put(deviceId, roomId);

            // 房间满员时发送统一的game_ready消息
            if (room.getPlayers().size() == 2) {
                // 准备游戏状态
                room.getGameState().setRoomId(room.getId());
                room.resetPlayerDecks();
                
                // 发送游戏准备消息，包含所有玩家信息和游戏开始指令
                JSONObject gameReadyMessage = new JSONObject();
                gameReadyMessage.put("type", "game_ready");
                gameReadyMessage.put("players", room.getPlayers());
                gameReadyMessage.put("roomId", room.getId());
                broadcastToRoom(room, gameReadyMessage);
                
                log.info("/match - Game ready for room {}, 2 players joined", roomId);
            } else {
                // 只有一个玩家时发送等待状态
                broadcastRoomState(room);
            }
        } else {
            Long roomId = GameUtil.getNextRomeId();
            Room room = new Room(roomId);
            user.setRole(GameUtil.RoleEnum.blueSide.toString());
            user.setMsgCode(GameUtil.BlUE_JOIN_GAME);
            room.addPlayer(user);
            rooms.put(roomId, room);
            roomCode2RoomIdMap.put(roomCode, roomId);
            deviceId2RoomIdMap.put(deviceId, roomId);
            
            // 只有一个玩家，发送等待状态
            broadcastRoomState(room);
        }
    }

    private void handlePlayCard(String deviceId, Integer card) {
        Long roomId = deviceId2RoomIdMap.get(deviceId);
        if (roomId == null) return;

        Room room = rooms.get(roomId);
        if (room == null || room.getGameState().isGameCompleted()) return;

        String role = room.getPlayers().stream()
                .filter(p -> p.getDeviceId().equals(deviceId))
                .findFirst()
                .map(PlayerVO::getRole)
                .orElse(null);
        if (role == null) return;

        room.getGameState().addCard(role, card);

        if (room.getGameState().isCurrentRoundComplete()) {
            JSONObject roundInfo = new JSONObject();
            roundInfo.put("type", "round_complete");
            roundInfo.put("round", room.getGameState().getRoundNumber());
            for (PlayerVO player : room.getPlayers()) {
                if (GameUtil.RoleEnum.redSide.toString().equals(player.getRole())) {
                    roundInfo.put("myCard", room.getGameState().getCurrentRedCard());
                    roundInfo.put("oppCard", room.getGameState().getCurrentBlueCard());
                } else {
                    roundInfo.put("myCard", room.getGameState().getCurrentBlueCard());
                    roundInfo.put("oppCard", room.getGameState().getCurrentRedCard());
                }
                GameUtil.sendMessage(player.getSession(), roundInfo);
            }

            String result = room.getGameState().determineRoundResult();
            if (GameState.RESULT_CONTINUE.equals(result)) {
                room.getGameState().nextRound();
            } else {
                PlayerVO redPlayer = room.getPlayers().stream()
                        .filter(p -> GameUtil.RoleEnum.redSide.toString().equals(p.getRole()))
                        .findFirst().orElse(null);
                PlayerVO bluePlayer = room.getPlayers().stream()
                        .filter(p -> GameUtil.RoleEnum.blueSide.toString().equals(p.getRole()))
                        .findFirst().orElse(null);
                if (redPlayer == null || bluePlayer == null) return;

                if (GameState.RESULT_RED_WIN.equals(result)) {
                    recordGame(roomId, redPlayer.getDeviceId(), bluePlayer.getDeviceId());
                } else if (GameState.RESULT_BLUE_WIN.equals(result)) {
                    recordGame(roomId, bluePlayer.getDeviceId(), redPlayer.getDeviceId());
                }

                JSONObject resultInfo = new JSONObject();
                resultInfo.put("type", "game_result");
                resultInfo.put("result", result);
                broadcastToRoom(room, resultInfo);
            }
        } else {
            PlayerVO opponent = room.getPlayers().stream()
                    .filter(p -> !p.getDeviceId().equals(deviceId))
                    .findFirst().orElse(null);
            if (opponent != null) {
                JSONObject takeTurnInfo = new JSONObject();
                takeTurnInfo.put("type", "please_take_card");
                GameUtil.sendMessage(opponent.getSession(), takeTurnInfo);
            }
        }
    }

    private void handleLeaveRoom(String deviceId) {
        // 防止重复处理同一设备的离开房间请求
        if (!leavingDeviceIds.add(deviceId)) {
            log.info("/match - Device {} is already being processed for leaving room, skipping", deviceId);
            return;
        }
        
        try {
            Long roomId = deviceId2RoomIdMap.get(deviceId);
            if (roomId == null) {
                log.info("/match - Device {} not in any room, cleaning up", deviceId);
                return;
            }

            Room room = rooms.get(roomId);
            if (room == null) {
                log.info("/match - Room {} not found for device {}, cleaning up", roomId, deviceId);
                return;
            }

            // 找到对方玩家
            PlayerVO opponent = room.getPlayers().stream()
                    .filter(p -> !p.getDeviceId().equals(deviceId))
                    .findFirst().orElse(null);
            
            // 只给对方发送离开消息，不要关闭对方连接
            if (opponent != null) {
                JSONObject response = new JSONObject();
                response.put("type", "opponent_leave");
                response.put("message", "对方离开了房间");
                try {
                    if (opponent.getSession() != null && opponent.getSession().isOpen()) {
                        GameUtil.sendMessage(opponent.getSession(), response);
                        log.info("/match - Sent opponent_leave message to deviceId: {}", opponent.getDeviceId());
                        // 重要：不要关闭对方连接！让对方自己决定是否离开
                        // opponent.getSession().close(); // 删除这行，避免触发对方的@OnClose
                    }
                } catch (Exception e) {
                    log.error("Error sending opponent_leave message to deviceId: {}", opponent.getDeviceId(), e);
                }
            }

            // 只关闭离开者自己的连接
            Session leaverSession = deviceId2SessionMap.get(deviceId);
            if (leaverSession != null && leaverSession.isOpen()) {
                try {
                    leaverSession.close();
                    log.info("/match - Closed leaver session for deviceId: {}", deviceId);
                } catch (Exception e) {
                    log.error("Error closing leaver session for deviceId: {}", deviceId, e);
                }
            }

            // 清理离开者的数据
            deviceId2SessionMap.remove(deviceId);
            deviceId2RoomIdMap.remove(deviceId);
            
            // ✅ 关键修复：从房间玩家列表中移除离开的玩家
            room.getPlayers().removeIf(p -> p.getDeviceId().equals(deviceId));
            
            // 检查房间是否还有其他在线玩家
            boolean hasOtherPlayers = room.getPlayers().stream()
                    .anyMatch(p -> deviceId2SessionMap.containsKey(p.getDeviceId()));
            
            if (!hasOtherPlayers) {
                // 房间里没有其他在线玩家，完全清理房间
                String roomCode = roomCode2RoomIdMap.entrySet().stream()
                        .filter(entry -> entry.getValue().equals(roomId))
                        .map(Map.Entry::getKey)
                        .findFirst().orElse(null);
                if (roomCode != null) {
                    roomCode2RoomIdMap.remove(roomCode);
                }
                rooms.remove(roomId);
                log.info("/match - Room {} cleaned up completely, deviceId {} left", roomId, deviceId);
            } else {
                log.info("/match - Player {} left room {}, room still has {} online players", 
                        deviceId, roomId, room.getPlayers().size());
            }
        } finally {
            // 无论如何都要从处理集合中移除，避免永久阻塞
            leavingDeviceIds.remove(deviceId);
        }
    }

    private void handleRematchRequest(String requesterId) {
        log.info("/match - Handling rematch request for deviceId: {}", requesterId);
        Long roomId = deviceId2RoomIdMap.get(requesterId);
        if (roomId == null) {
            log.warn("/match - No room found for requesterId: {}", requesterId);
            return;
        }

        Room room = rooms.get(roomId);
        if (room == null || room.getPlayers().size() != 2) {
            log.warn("/match - Invalid room state for rematch, roomId: {}, players: {}", roomId, room == null ? 0 : room.getPlayers().size());
            handleLeaveRoom(requesterId); // 清理房间
            return;
        }

        PlayerVO requester = room.getPlayers().stream()
                .filter(p -> p.getDeviceId().equals(requesterId))
                .findFirst().orElse(null);
        PlayerVO opponent = room.getPlayers().stream()
                .filter(p -> !p.getDeviceId().equals(requesterId))
                .findFirst().orElse(null);
        if (requester == null || opponent == null) {
            log.warn("/match - Requester or opponent not found, requesterId: {}", requesterId);
            handleLeaveRoom(requesterId); // 清理房间
            return;
        }

        Session opponentSession = opponent.getSession();
        if (opponentSession == null || !opponentSession.isOpen()) {
            log.warn("/match - Opponent session not available for deviceId: {}", opponent.getDeviceId());
            // 通知请求方对方已离开
            JSONObject response = new JSONObject();
            response.put("type", "opponent_leave");
            response.put("message", "玩家离开了房间");
            GameUtil.sendMessage(requester.getSession(), response);
            handleLeaveRoom(requesterId); // 清理房间
            return;
        }

        JSONObject rematchRequest = new JSONObject();
        rematchRequest.put("type", "rematch_request");
        rematchRequest.put("from", requesterId);
        try {
            GameUtil.sendMessage(opponentSession, rematchRequest);
            log.info("/match - Sent rematch_request to opponent deviceId: {}", opponent.getDeviceId());
        } catch (Exception e) {
            log.error("/match - Failed to send rematch_request to opponent deviceId: {}", opponent.getDeviceId(), e);
            // 通知请求方对方已离开
            JSONObject response = new JSONObject();
            response.put("type", "opponent_leave");
            response.put("message", "玩家离开了房间");
            GameUtil.sendMessage(requester.getSession(), response);
            handleLeaveRoom(requesterId); // 清理房间
        }
    }

    private void handleRematchAccept(String deviceId) {
        Long roomId = deviceId2RoomIdMap.get(deviceId);
        if (roomId == null) return;

        Room room = rooms.get(roomId);
        if (room == null || room.getPlayers().size() != 2) return;

        // 重置游戏状态并开始新游戏
        room.getGameState().reset();
        room.resetPlayerDecks();

        // 发送再战开始消息
        JSONObject gameReadyMessage = new JSONObject();
        gameReadyMessage.put("type", "game_ready");
        gameReadyMessage.put("players", room.getPlayers());
        gameReadyMessage.put("roomId", room.getId());
        broadcastToRoom(room, gameReadyMessage);
    }

    private void handleRematchReject(String deviceId) {
        Long roomId = deviceId2RoomIdMap.get(deviceId);
        if (roomId == null) {
            log.warn("/match - No room found for deviceId: {}", deviceId);
            return;
        }

        Room room = rooms.get(roomId);
        if (room == null || room.getPlayers().size() != 2) {
            log.warn("/match - Invalid room state for rematch reject, roomId: {}, players: {}", roomId, room == null ? 0 : room.getPlayers().size());
            return;
        }

        // 找到拒绝方和请求方
        PlayerVO rejecter = room.getPlayers().stream()
                .filter(p -> p.getDeviceId().equals(deviceId))
                .findFirst().orElse(null);
        PlayerVO requester = room.getPlayers().stream()
                .filter(p -> !p.getDeviceId().equals(deviceId))
                .findFirst().orElse(null);
        
        if (rejecter == null || requester == null) {
            log.warn("/match - Rejecter or requester not found for deviceId: {}", deviceId);
            return;
        }

        log.info("/match - Processing rematch reject: rejecter={}, requester={}", rejecter.getDeviceId(), requester.getDeviceId());

        // 检查请求方的连接状态
        if (requester.getSession() == null || !requester.getSession().isOpen()) {
            log.info("/match - Requester {} session is not available, no need to send reject message", requester.getDeviceId());
            // 对方已经断开连接，不需要发送任何消息
            // 拒绝方留在游戏界面，进入复盘模式
            return;
        }

        // 只给请求方发送拒绝消息
        JSONObject response = new JSONObject();
        response.put("type", "rematch_reject");
        try {
            GameUtil.sendMessage(requester.getSession(), response);
            log.info("/match - Sent rematch_reject to requester deviceId: {}", requester.getDeviceId());
        } catch (Exception e) {
            log.error("/match - Failed to send rematch_reject to requester deviceId: {}", requester.getDeviceId(), e);
            // 发送失败，说明对方连接有问题，但不需要做任何处理
            // 拒绝方仍然留在游戏界面
        }
        
        // 重要：不调用handleLeaveRoom！
        // 拒绝方留在游戏界面进入复盘模式
        // 请求方收到rematch_reject消息后自己决定是否离开
        log.info("/match - Rematch reject processed successfully, rejecter stays in game for replay mode");
    }

    private void notifyTakeCard(Room room) {
        JSONObject takeTurnInfo = new JSONObject();
        takeTurnInfo.put("type", "please_take_card");
        broadcastToRoom(room, takeTurnInfo);
    }

    private void broadcastRoomState(Room room) {
        List<PlayerVO> players = room.getPlayers();
        JSONObject roomState = new JSONObject();
        roomState.put("type", "room_state");
        roomState.put("players", players);
        broadcastToRoom(room, roomState);
    }

    private void broadcastToRoom(Room room, Object message) {
        for (PlayerVO player : room.getPlayers()) {
            Session session = player.getSession();
            if (session != null && session.isOpen()) {
                GameUtil.sendMessage(session, message);
            } else {
                log.warn("/match - Skipping message broadcast to unavailable session for deviceId: {}", player.getDeviceId());
            }
        }
    }

    private void recordGame(Long roomId, String winner, String loser) {
        Room room = rooms.get(roomId);
        if (room == null) return;

        GameState gameState = room.getGameState();
        gameState.recordGameResult(winner, loser);
        GameState gameRecord = gameState.createRecord();

        playerGameRecords.computeIfAbsent(winner, k -> new ArrayList<>()).add(gameRecord);
        playerGameRecords.computeIfAbsent(loser, k -> new ArrayList<>()).add(gameRecord);

        long winStreak = calculateWinStreak(winner, roomId);
        if (winStreak >= 3) {
            JSONObject shareMessage = new JSONObject();
            shareMessage.put("type", "share");
            String winnerName = room.getPlayers().stream()
                    .filter(p -> p.getDeviceId().equals(winner))
                    .map(PlayerVO::getNickName)
                    .findFirst().orElse("玩家");
            String loserName = room.getPlayers().stream()
                    .filter(p -> p.getDeviceId().equals(loser))
                    .map(PlayerVO::getNickName)
                    .findFirst().orElse("对手");
            String shareCode = "WIN" + winStreak;
            shareMessage.put("shareCode", shareCode);
            shareMessage.put("winnerName", winnerName);
            shareMessage.put("loserName", loserName);

            switch (shareCode) {
                case "WIN3":
                    shareMessage.put("title", "连战连捷！");
                    break;
                case "WIN4":
                    shareMessage.put("title", "爆锤！");
                    break;
                case "WIN5":
                    shareMessage.put("title", "超神！");
                    break;
            }

            Session winnerSession = deviceId2SessionMap.get(winner);
            if (winnerSession != null && winnerSession.isOpen()) {
                GameUtil.sendMessage(winnerSession, shareMessage);
            }
        }
    }

//    private long calculateWinStreak(String playerId, Long roomId) {
//        List<GameState> records = playerGameRecords.get(playerId);
//        if (records == null) return 0;
//
//        return records.stream()
//                .sorted((a, b) -> b.getGameEndTime().compareTo(a.getGameEndTime()))
//                .takeWhile(record -> record.getRoomId().equals(roomId) && record.getWinner().equals(playerId))
//                .count();
//    }
private long calculateWinStreak(String playerId, Long roomId) {
    List<GameState> records = playerGameRecords.get(playerId);
    if (records == null) return 0;

    // 按结束时间降序排序
    records.sort((a, b) -> b.getGameEndTime().compareTo(a.getGameEndTime()));

    long count = 0;
    for (GameState record : records) {
        if (record.getRoomId().equals(roomId) && record.getWinner().equals(playerId)) {
            count++;
        } else {
            break;
        }
    }
    return count;
}


    /**
     * 处理玩家断开连接（业务逻辑层）
     * 只在确实需要时通知对方离开
     */
    private void handlePlayerDisconnect(String deviceId) {
        Long roomId = deviceId2RoomIdMap.get(deviceId);
        if (roomId == null) return;

        Room room = rooms.get(roomId);
        if (room == null) return;

        // 找到对方玩家
        PlayerVO opponent = room.getPlayers().stream()
                .filter(p -> !p.getDeviceId().equals(deviceId))
                .findFirst().orElse(null);
        
        // 只给对方发送离开消息，让对方知道连接已断开
        if (opponent != null && opponent.getSession() != null && opponent.getSession().isOpen()) {
            JSONObject response = new JSONObject();
            response.put("type", "opponent_leave");
            response.put("message", "对方离开了房间");
            try {
                GameUtil.sendMessage(opponent.getSession(), response);
                log.info("/match - Sent opponent_leave message to deviceId: {}", opponent.getDeviceId());
            } catch (Exception e) {
                log.error("Error sending opponent_leave message to deviceId: {}", opponent.getDeviceId(), e);
            }
        }

        // 清理断开连接玩家的数据
        cleanupPlayerData(deviceId);
    }

    /**
     * 清理玩家数据（资源管理层）
     */
    private void cleanupPlayerData(String deviceId) {
        Long roomId = deviceId2RoomIdMap.get(deviceId);
        deviceId2RoomIdMap.remove(deviceId);
        
        if (roomId != null) {
            Room room = rooms.get(roomId);
            if (room != null) {
                // ✅ 关键修复：先从房间玩家列表中移除离开的玩家
                room.getPlayers().removeIf(p -> p.getDeviceId().equals(deviceId));
                
                // 检查房间是否还有其他在线玩家
                boolean hasOtherPlayers = room.getPlayers().stream()
                        .anyMatch(p -> deviceId2SessionMap.containsKey(p.getDeviceId()));
                
                if (!hasOtherPlayers) {
                    // 房间里没有其他在线玩家，清理房间
                    String roomCode = roomCode2RoomIdMap.entrySet().stream()
                            .filter(entry -> entry.getValue().equals(roomId))
                            .map(Map.Entry::getKey)
                            .findFirst().orElse(null);
                    if (roomCode != null) {
                        roomCode2RoomIdMap.remove(roomCode);
                    }
                    rooms.remove(roomId);
                    log.info("/match - Room {} cleaned up completely, deviceId {} left", roomId, deviceId);
                } else {
                    log.info("/match - Player {} left room {}, room still has {} online players", 
                            deviceId, roomId, room.getPlayers().size());
                }
            }
        }
    }
    
    /**
     * 发送错误消息
     */
    private void sendErrorMessage(Session session, String message) {
        JSONObject response = new JSONObject();
        response.put("type", "nickname_error");
        response.put("message", message);
        GameUtil.sendMessage(session, response);
    }
}

class Room {
    private final Long id;
    private final List<PlayerVO> players = new ArrayList<>();
    private final GameState gameState = new GameState();
    private final Map<String, List<Integer>> playerDecks = new HashMap<>();

    public Room(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public List<PlayerVO> getPlayers() {
        return players;
    }

    public GameState getGameState() {
        return gameState;
    }

    public void addPlayer(PlayerVO player) {
        players.add(player);
    }

    public void resetPlayerDecks() {
        for (PlayerVO player : players) {
            playerDecks.put(player.getDeviceId(), new ArrayList<>(
                    IntStream.rangeClosed(1, GameState.TEN).boxed().collect(Collectors.toList())
            ));
        }
    }

    public boolean validateCard(String deviceId, Integer card) {
        List<Integer> deck = playerDecks.get(deviceId);
        return deck != null && deck.contains(card);
    }

    public void removeCard(String deviceId, Integer card) {
        List<Integer> deck = playerDecks.get(deviceId);
        if (deck != null) {
            deck.remove(card);
        }
    }
}