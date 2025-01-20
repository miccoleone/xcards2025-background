package com.tencard.demo01;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.tencard.demo01.saveData.PlayerRepository;
import com.tencard.demo01.saveData.PlayerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * yue 2021/8/17
 */
@Component
@ServerEndpoint("/room") // 改为room
@Slf4j
public class WebSocket4Room {

    @Autowired
    private PlayerService playerService;

    @Autowired
    private PlayerRepository playerRepository;

    // 房间code -> 房间Id 的映射
    public static Map<String, Long> roomCode2RoomIdMap = new ConcurrentHashMap<>(); // <4396,100007>
    // 房间Id -> List<UserVO> 两个玩家列表
    public static Map<Long, List<UserVO>> roomId2UserListMap = new ConcurrentHashMap<>();


    // 设备id与会话的映射 更新保持最新
    public static Map<String, Session> deviceId2SessionMap_Room = new ConcurrentHashMap<>();
    // deviceId -> roomId 映射
    public static Map<String,Long> deviceId2RoomIdMap = new ConcurrentHashMap<>();

    public WebSocket4Room(){
        log.info("/room ------------- 新建了一个WebSocket4Room对象----------------");
    }

    @OnOpen
    public void onOpen(Session session) {
        // 从 URL 查询参数获取 deviceId（如果通过URL传递）
        String deviceId = session.getRequestParameterMap().get("deviceId").get(0);

        if (deviceId != null) {
            // 更新session映射，处理可能的重复连接
            Session oldSession = deviceId2SessionMap_Room.put(deviceId, session);
            if (oldSession != null && !oldSession.equals(session)) {
                try {
                    oldSession.close();
                } catch (IOException e) {
                    log.error("Error closing old session", e);
                }
            }
            log.info("/room - Device ID: {} connected with session ID: {}", deviceId, session.getId());
        } else {
            log.warn("/room - No deviceId found in the connection request");
        }
    }

    @OnClose
    public void onClose(Session session) {
        log.info("/room xxxxxxxxxxx   要清掉一个session数据辣 sessionId: {}  xxxxxxxxxxx ",session.getId());
        // 查找对应的 deviceId
        String deviceId = findDeviceIdBySession(session);
        if (deviceId != null) {
            // 移除 session
            deviceId2SessionMap_Room.remove(deviceId);
            log.info("/room - Device ID: {} disconnected. Removed from deviceId2SessionMap", deviceId);
        } else {
            log.warn("/room - Session closed, but deviceId not found. Could not remove from deviceId2SessionMap");
        }
    }

    // 根据 session 查找对应的 deviceId
    private String findDeviceIdBySession(Session session) {
        for (Map.Entry<String, Session> entry : deviceId2SessionMap_Room.entrySet()) {
            if (entry.getValue().equals(session)) {
                return entry.getKey();
            }
        }
        return null;
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        log.info("/room - 收到消息：{}", message);
        try {
            // 解析消息
            JSONObject jsonMessage = JSON.parseObject(message);
            String type = jsonMessage.getString("type");
            String deviceId = jsonMessage.getString("deviceId");

            // 处理再战相关消息
            if ("rematch_request".equals(type)) {
                handleRematchRequest(deviceId);
                return;
            } else if ("rematch_accept".equals(type)) {
                handleRematchAccept(deviceId);
                return;
            } else if ("rematch_reject".equals(type)) {
                handleRematchReject(deviceId);
                return;
            }
            
            // 处理加入房间请求
            UserVO user = JSON.parseObject(message, UserVO.class);
            if (user.deviceId == null || user.roomCode == null) {
                log.error("/room - Invalid message: deviceId or roomCode is null");
                return;
            }
            handleJoinRoom(user, session);
            
        } catch (Exception e) {
            log.error("/room - Error processing message: ", e);
        }
    }

    // 处理再战请求
    private void handleRematchRequest(String requesterId) {
        // 获取房间ID
        Long roomId = deviceId2RoomIdMap.get(requesterId);
        if (roomId == null) return;

        // 获取房间内的玩家
        List<UserVO> players = roomId2UserListMap.get(roomId);
        if (players == null || players.size() != 2) return;

        // 找到对手
        UserVO opponent = players.stream()
                .filter(p -> !p.deviceId.equals(requesterId))
                .findFirst()
                .orElse(null);
        
        if (opponent == null) return;

        // 发送再战请求给对手
        Session opponentSession = deviceId2SessionMap_Room.get(opponent.deviceId);
        if (opponentSession != null && opponentSession.isOpen()) {
            JSONObject rematchRequest = new JSONObject();
            rematchRequest.put("type", "rematch_request");
            rematchRequest.put("from", requesterId);
            sendMessage(opponentSession, rematchRequest.toJSONString());
        }
    }

    // 处理接受再战请求
    private void handleRematchAccept(String deviceId) {
        // 获取房间ID
        Long roomId = deviceId2RoomIdMap.get(deviceId);
        if (roomId == null) return;

        // 获取房间内的玩家
        List<UserVO> players = roomId2UserListMap.get(roomId);
        if (players == null || players.size() != 2) return;

        // 重置游戏状态
        WebSocket4Game.resetGameState(roomId);

        // 通知双方重新开始游戏
        players.forEach(player -> {
            Session playerSession = deviceId2SessionMap_Room.get(player.deviceId);
            if (playerSession != null && playerSession.isOpen()) {
                JSONObject response = new JSONObject();
                response.put("type", "rematch_accept");
                sendMessage(playerSession, response.toJSONString());
            }
        });
    }

    private void handleRematchResponse(UserVO user) {
        if ("rematch_accept".equals(user.type)) {
            // 重置游戏状态
            Long roomId = deviceId2RoomIdMap.get(user.deviceId);
            if (roomId != null) {
                WebSocket4Game.resetGameState(roomId);
            }
        }
        // 通知发起方响应结果
        notifyOpponent(user, user.type);
    }

    private void handleJoinRoom(UserVO user, Session session) {
        log.info("/room - Processing join room request for room: {}", user.roomCode);
        
        user.setWinRate(49);
        user.setSession(session);
        user.setSessionId(session.getId());
        
        if (roomCode2RoomIdMap.containsKey(user.roomCode)) {
            log.info("/room - Found existing room with code: {}", user.roomCode);
            Long roomId = roomCode2RoomIdMap.get(user.roomCode);
            List<UserVO> existingUsers = roomId2UserListMap.get(roomId);
            
            // 检查房间是否已满
            if (existingUsers != null && existingUsers.size() >= 2) {
                // 发送房间已满的消息
                UserVO response = new UserVO();
                response.setType("room_full");
                sendMessage(session, JSON.toJSONString(response));
                return;
            }
            
            // 红色方
            user.setRole(GameUtil.RoleEnum.redSide.toString());
            // 后进入游戏的玩家到场 给双发发送消息，让两人的页面都显示对方的信息
            user.setMsgCode(GameUtil.RED_JOIN_GAME); // 60
            deviceId2RoomIdMap.put(user.deviceId,roomId);
            roomId2UserListMap.computeIfAbsent(roomId, k -> new ArrayList<>()).add(user);
            updateBluePlayerSessionData(roomId2UserListMap,roomId);// 更新房主的session信息

            // 获取最新的用户列表
            final List<UserVO> updatedUsers = roomId2UserListMap.get(roomId);
            updatedUsers.forEach(e -> {
                /**
                 * 这里的逻辑是：1.红色方的room.html接到信息，它跳转到游戏画面展示双方信息
                 *            2.蓝色方(房主)在游戏页面接收到websocket的onmessage对手信息，要显示敌方信息
                 *  */
                if(e.session == null) return;
                log.info("/room ---------- 发送消息 sessionId: {},userRole:{}", e.session.getId(), e.role);
                sendMessage(e.session, updatedUsers);
            });
        } else {
            log.info("/room - Creating new room with code: {}", user.roomCode);
            // 新建一个房间Id 蓝色方
            user.setRole(GameUtil.RoleEnum.blueSide.toString());
            user.setMsgCode(GameUtil.BlUE_JOIN_GAME); // 50
            Long roomId = GameUtil.getNextRomeId();
            roomCode2RoomIdMap.put(user.roomCode, roomId);  // <"4396",10001L>
            deviceId2RoomIdMap.put(user.deviceId,roomId);
            roomId2UserListMap.computeIfAbsent(roomId, k -> new ArrayList<>()).add(user); // <10001L,List<user> users>
            // 房主到场 展示自己的牌
            sendMessage(user.session, roomId2UserListMap.get(roomId));
        }
    }

    private void updateBluePlayerSessionData(Map<Long, List<UserVO>> roomId2UserListMap, Long roomId) {
        try{
            UserVO red = roomId2UserListMap.get(roomId).stream().findFirst().get();
            log.info("/room ////////////// 房主 {} 之前的sessionid是 {}",roomId2UserListMap.get(roomId).stream().findFirst().get().deviceId,roomId2UserListMap.get(roomId).stream().findFirst().get().session.getId());
            red.setSession(deviceId2SessionMap_Room.get(red.deviceId));
            log.info("/room ////////////// 房主 {} 之后的sessionid是 {}",roomId2UserListMap.get(roomId).stream().findFirst().get().deviceId,roomId2UserListMap.get(roomId).stream().findFirst().get().session.getId());

        }catch (Exception e){
            log.error("/room //////////////  updateBluePlayerSessionData error : {}",e.toString());
        }
    }

    private void notifyOpponent(UserVO user, String type) {
        log.info("xxxxxxxxxxxxxxxxx--------------------------- notifyOpponent 被执行  xxxxxxxxxxxxxxxxx---------------------------  ");
        // 获取对手的User对象
        UserVO opponent = roomId2UserListMap.get(deviceId2RoomIdMap.get(user.deviceId)).stream()
                .filter(u -> !u.deviceId.equals(user.deviceId))
                .findFirst()
                .orElse(null);

        if (opponent == null) {
            log.warn("/room - Cannot find opponent for user: {}", user.deviceId);
            return;
        }

        Session opponentSession = deviceId2SessionMap_Room.get(opponent.deviceId);
        Session currentSession = deviceId2SessionMap_Room.get(user.deviceId);

        switch (type) {
            case "rematch_request":
                // 转发继续游戏请求给对手
                if (opponentSession != null && opponentSession.isOpen()) {
                    GameUtil.sendMessage(opponentSession, JSON.toJSONString(user));
                }
                break;

            case "rematch_accept":
                // 通知双方重置游戏状态
                if (currentSession != null && currentSession.isOpen()) {
                    GameUtil.sendMessage(currentSession, JSON.toJSONString(user));
                }
                if (opponentSession != null && opponentSession.isOpen()) {
                    GameUtil.sendMessage(opponentSession, JSON.toJSONString(user));
                }
                // 重置游戏状态
                WebSocket4Game.resetGameState(deviceId2RoomIdMap.get(user.deviceId));
                break;

            case "rematch_reject":
                // 通知请求方对方拒绝
                if (opponentSession != null && opponentSession.isOpen()) {
                    GameUtil.sendMessage(opponentSession, JSON.toJSONString(user));
                }
                // todo 这里做清空 map 的一些操作
                log.info("xxxxxxxxxxxxxxxxx------------------  这里做清空 map 的一些操作  xxxxxxxxxxxxxxxxx---------------------------  ");
                break;
        }
    }

    private void sendMessage(Session session, String message) {
        try {
            session.getBasicRemote().sendText(message);
        } catch (IOException e) {
            log.error("/room - Error sending message: {}", e.getMessage());
        }
    }

    private void sendMessage(Session session, List<UserVO> users) {
        try {
            session.getBasicRemote().sendText(JSON.toJSONString(users));
        } catch (IOException e) {
            log.error("/room - Error sending message: {}", e.getMessage());
        }
    }

    // 处理拒绝再战请求
    private void handleRematchReject(String deviceId) {
        // 获取房间ID
        Long roomId = deviceId2RoomIdMap.get(deviceId);
        if (roomId == null) return;

        // 获取房间内的玩家
        List<UserVO> players = roomId2UserListMap.get(roomId);
        if (players == null || players.size() != 2) return;

        // 找到发起请求的玩家
        UserVO requester = players.stream()
                .filter(p -> !p.deviceId.equals(deviceId))  // deviceId是拒绝的玩家，所以另一个是发起请求的玩家
                .findFirst()
                .orElse(null);

        if (requester == null) return;

        // 发送拒绝消息给发起请求的玩家
        Session requesterSession = deviceId2SessionMap_Room.get(requester.deviceId);
        if (requesterSession != null && requesterSession.isOpen()) {
            JSONObject response = new JSONObject();
            response.put("type", "rematch_reject");
            sendMessage(requesterSession, response.toJSONString());
        }

        // 3. 清理房间相关数据
        WebSocket4Game.roomStataMap.remove(roomId);
        roomId2UserListMap.remove(roomId);
        deviceId2RoomIdMap.remove(deviceId);
    }
}


