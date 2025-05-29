// package com.tencard.demo01;

// import com.alibaba.fastjson.JSON;
// import com.alibaba.fastjson.JSONObject;
// import com.tencard.demo01.saveData.Player;
// import com.tencard.demo01.saveData.PlayerRepository;
// import com.tencard.demo01.saveData.PlayerService;
// import lombok.extern.slf4j.Slf4j;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.stereotype.Component;

// import javax.websocket.OnClose;
// import javax.websocket.OnMessage;
// import javax.websocket.OnOpen;
// import javax.websocket.Session;
// import javax.websocket.server.ServerEndpoint;
// import java.io.IOException;
// import java.util.ArrayList;
// import java.util.List;
// import java.util.Map;
// import java.util.concurrent.ConcurrentHashMap;


// /**
//  * yue 2021/8/17
//  */
// @Component
// @ServerEndpoint("/room") // 改为room
// @Slf4j
// public class WebSocket4Room {

//     private static PlayerService playerService;
//     private static PlayerRepository playerRepository;

//     @Autowired
//     public void setPlayerService(PlayerService service) {
//         playerService = service;
//     }

//     @Autowired
//     public void setPlayerRepository(PlayerRepository repository) {
//         playerRepository = repository;
//     }

//     // 房间code -> 房间Id 的映射
//     public static Map<String, Long> roomCode2RoomIdMap = new ConcurrentHashMap<>(); // <4396,100007>
//     // 房间Id -> List<UserVO> 两个玩家列表
//     public static Map<Long, List<UserVO>> roomId2UserListMap = new ConcurrentHashMap<>();


//     // 设备id与会话的映射 更新保持最新
//     public static Map<String, Session> deviceId2SessionMap_Room = new ConcurrentHashMap<>();
//     // deviceId -> roomId 映射
//     public static Map<String,Long> deviceId2RoomIdMap = new ConcurrentHashMap<>();

//     public WebSocket4Room(){
//         log.info("/room ------------- 新建了一个WebSocket4Room对象----------------");
//     }

//     @OnOpen
//     public void onOpen(Session session) {
//         // 从 URL 查询参数获取 deviceId（如果通过URL传递）
//         String deviceId = session.getRequestParameterMap().get("deviceId").get(0);

//         if (deviceId != null) {
//             // 更新session映射，处理可能的重复连接
//             Session oldSession = deviceId2SessionMap_Room.put(deviceId, session);
//             if (oldSession != null && !oldSession.equals(session)) {
//                 try {
//                     oldSession.close();
//                 } catch (IOException e) {
//                     log.error("Error closing old session", e);
//                 }
//             }
//             log.info("/room - Device ID: {} connected with session ID: {}", deviceId, session.getId());
//         } else {
//             log.warn("/room - No deviceId found in the connection request");
//         }
//     }

//     @OnClose
//     public void onClose(Session session) {
//         log.info("/room xxxxxxxxxxx   要清掉一个session数据辣 sessionId: {}  xxxxxxxxxxx ",session.getId());
//         // 查找对应的 deviceId
//         String deviceId = findDeviceIdBySession(session);
//         if (deviceId != null) {
//             // 移除 session
//             deviceId2SessionMap_Room.remove(deviceId);
//             log.info("/room - Device ID: {} disconnected. Removed from deviceId2SessionMap", deviceId);
//         } else {
//             log.warn("/room - Session closed, but deviceId not found. Could not remove from deviceId2SessionMap");
//         }
//     }

//     // 根据 session 查找对应的 deviceId
//     private String findDeviceIdBySession(Session session) {
//         for (Map.Entry<String, Session> entry : deviceId2SessionMap_Room.entrySet()) {
//             if (entry.getValue().equals(session)) {
//                 return entry.getKey();
//             }
//         }
//         return null;
//     }

//     @OnMessage
//     public void onMessage(String message, Session session) {
//         log.info("/room - 收到消息：{}", message);
//         try {
//             // 解析消息
//             JSONObject jsonMessage = JSON.parseObject(message);
//             String type = jsonMessage.getString("type");
//             String deviceId = jsonMessage.getString("deviceId");

//             // 处理再战相关消息
//             if ("rematch_request".equals(type)) {
//                 handleRematchRequest(deviceId);
//                 return;
//             } else if ("rematch_accept".equals(type)) {
//                 handleRematchAccept(deviceId);
//                 return;
//             } else if ("rematch_reject".equals(type)) {
//                 handleRematchReject(deviceId);
//                 return;
//             } else if ("opponent_leave".equals(type)) {
//                 handleOpponentLeave(deviceId);
//                 return;
//             }
            
//             // 处理加入房间请求
//             UserVO user = JSON.parseObject(message, UserVO.class);
//             if (user.deviceId == null || user.roomCode == null) {
//                 log.error("/room - Invalid message: deviceId or roomCode is null");
//                 return;
//             }
//             // 根据 deviceId判断是否有这个用户，如果没有那就新建一个
//             checkPlayerInfo(user);
//             handleJoinRoom(user, session);
            
//         } catch (Exception e) {
//             log.error("/room - Error processing message: ", e);
//         }
//     }

//     private void checkPlayerInfo(UserVO user) {
//         try {
//             // 查找玩家
//             Player existingPlayer = playerService.findByDeviceId(user.deviceId);
            
//             if (existingPlayer == null) {
//                 // 如果玩家不存在，创建新玩家
//                 Player newPlayer = new Player();
//                 newPlayer.setDeviceId(user.deviceId);
//                 newPlayer.setNickName(user.nickName);
//                 newPlayer.setWins(0);
//                 newPlayer.setLosses(0);
                
//                 playerService.savePlayer(newPlayer);
//                 log.info("Created new player: deviceId={}, nickname={}", user.deviceId, user.nickName);
//             }
//         } catch (Exception e) {
//             log.error("Error checking player info: ", e);
//         }
//     }

//     // 处理再战请求
//     private void handleRematchRequest(String requesterId) {
//         // 获取房间ID
//         Long roomId = deviceId2RoomIdMap.get(requesterId);
//         if (roomId == null) return;

//         // 获取房间内的玩家
//         List<UserVO> players = roomId2UserListMap.get(roomId);
//         if (players == null || players.size() != 2) return;

//         // 找到对手
//         UserVO opponent = players.stream()
//                 .filter(p -> !p.deviceId.equals(requesterId))
//                 .findFirst()
//                 .orElse(null);
        
//         if (opponent == null) return;

//         // 发送再战请求给对手
//         Session opponentSession = deviceId2SessionMap_Room.get(opponent.deviceId);
//         if (opponentSession != null && opponentSession.isOpen()) {
//             JSONObject rematchRequest = new JSONObject();
//             rematchRequest.put("type", "rematch_request");
//             rematchRequest.put("from", requesterId);
//             sendMessage(opponentSession, rematchRequest.toJSONString());
//         } else {
//             log.warn("/room - Cannot send rematch request: opponent session is null or closed");
//         }
//     }

//     // 处理接受再战请求
//     private void handleRematchAccept(String deviceId) {
//         // 获取房间ID
//         Long roomId = deviceId2RoomIdMap.get(deviceId);
//         if (roomId == null) return;

//         // 获取房间内的玩家
//         List<UserVO> players = roomId2UserListMap.get(roomId);
//         if (players == null || players.size() != 2) return;

//         // 重置游戏状态
//         WebSocket4Game.resetGameState(roomId);

//         // 通知双方重新开始游戏
//         players.forEach(player -> {
//             Session playerSession = deviceId2SessionMap_Room.get(player.deviceId);
//             if (playerSession != null && playerSession.isOpen()) {
//                 JSONObject response = new JSONObject();
//                 response.put("type", "rematch_accept");
//                 sendMessage(playerSession, response.toJSONString());
//             } else {
//                 log.warn("/room - Cannot send rematch accept: player session for {} is null or closed", player.deviceId);
//             }
//         });
//     }

//     private void handleJoinRoom(UserVO user, Session session) {
//         log.info("/room - Processing join room request for room: {}", user.roomCode);
        
//         user.setWinRate(49);
//         user.setSession(session);
//         user.setSessionId(session.getId());
        
//         if (roomCode2RoomIdMap.containsKey(user.roomCode)) {
//             log.info("/room - Found existing room with code: {}", user.roomCode);
//             Long roomId = roomCode2RoomIdMap.get(user.roomCode);
//             List<UserVO> existingUsers = roomId2UserListMap.get(roomId);
            
//             // 检查是否是房间中的玩家重新连接
//             boolean isReconnect = false;
//             if (existingUsers != null) {
//                 for (int i = 0; i < existingUsers.size(); i++) {
//                     UserVO existingUser = existingUsers.get(i);
//                     // 如果是同一个设备ID的用户重新连接，更新其会话信息
//                     if (existingUser.deviceId.equals(user.deviceId)) {
//                         log.info("/room - User {} reconnecting, updating session information", user.deviceId);
//                         // 保留原有角色和其他信息，只更新会话相关信息
//                         existingUser.setSession(session);
//                         existingUser.setSessionId(session.getId());
//                         isReconnect = true;
//                         break;
//                     }
//                 }
//             }
            
//             // 如果不是重连，则检查房间是否已满
//             if (!isReconnect) {
//                 // 检查房间是否已满
//                 if (existingUsers != null && existingUsers.size() >= 2) {
//                     // 发送房间已满的消息
//                     UserVO response = new UserVO();
//                     response.setType("room_full");
//                     sendMessage(session, JSON.toJSONString(response));
//                     return;
//                 }
                
//                 // 作为新用户加入(红色方)
//                 user.setRole(GameUtil.RoleEnum.redSide.toString());
//                 // 后进入游戏的玩家到场 给双发发送消息，让两人的页面都显示对方的信息
//                 user.setMsgCode(GameUtil.RED_JOIN_GAME); // 60
//                 deviceId2RoomIdMap.put(user.deviceId, roomId);
//                 log.info("/进入房间！ {} ,roomI:{}, deviceId2RoomIdMap:{} ", user.nickName, roomId, deviceId2RoomIdMap);
//                 roomId2UserListMap.computeIfAbsent(roomId, k -> new ArrayList<>()).add(user);
//             }

//             // 获取最新的用户列表
//             final List<UserVO> updatedUsers = roomId2UserListMap.get(roomId);
//             // 确保发送消息前检查会话是否有效
//             updatedUsers.forEach(e -> {
//                 if (e.session != null && e.session.isOpen()) {
//                     log.info("/room ---------- 发送消息 sessionId: {},userRole:{},message:{}", e.session.getId(), e.role, updatedUsers);
//                     sendMessage(e.session, updatedUsers);
//                 } else {
//                     log.warn("/room - Session for user {} is null or closed, skipping message send", e.deviceId);
//                 }
//             });
//         } else {
//             log.info("/room - Creating new room with code: {}", user.roomCode);
//             // 新建一个房间Id 蓝色方
//             user.setRole(GameUtil.RoleEnum.blueSide.toString());
//             user.setMsgCode(GameUtil.BlUE_JOIN_GAME); // 50
//             Long roomId = GameUtil.getNextRomeId();
//             roomCode2RoomIdMap.put(user.roomCode, roomId);  // <"4396",10001L>
//             deviceId2RoomIdMap.put(user.deviceId,roomId);
//             log.info("/进入房间！ {} ,roomI:{}, deviceId2RoomIdMap:{} ", user.nickName,roomId,deviceId2RoomIdMap);
//             roomId2UserListMap.computeIfAbsent(roomId, k -> new ArrayList<>()).add(user); // <10001L,List<user> users>
//             // 房主到场 展示自己的牌
//             sendMessage(user.session, roomId2UserListMap.get(roomId));
//         }
//     }

//     private void sendMessage(Session session, String message) {
//         try {
//             if (session != null && session.isOpen()) {
//                 session.getBasicRemote().sendText(message);
//             } else {
//                 log.warn("/room - Cannot send message: session is null or closed");
//             }
//         } catch (IOException e) {
//             log.error("/room - Error sending message: {}", e.getMessage());
//         }
//     }

//     private void sendMessage(Session session, List<UserVO> users) {
//         try {
//             if (session != null && session.isOpen()) {
//                 session.getBasicRemote().sendText(JSON.toJSONString(users));
//             } else {
//                 log.warn("/room - Cannot send message: session is null or closed");
//             }
//         } catch (IOException e) {
//             log.error("/room - Error sending message: {}", e.getMessage());
//         }
//     }

//     // 处理拒绝再战请求
//     private void handleRematchReject(String deviceId) {
//         // 直接按离开房间处理，统一逻辑
//         handleOpponentLeave(deviceId);
//     }

//     // 处理玩家主动离开房间
//     private void handleOpponentLeave(String deviceId) {
//         Long roomId = deviceId2RoomIdMap.get(deviceId);
//         if (roomId == null) return;
//         List<UserVO> players = roomId2UserListMap.get(roomId);
//         if (players == null) return;
//         // 通知对方"对方离开了房间"
//         for (UserVO player : players) {
//             if (!player.deviceId.equals(deviceId)) {
//                 Session otherSession = deviceId2SessionMap_Room.get(player.deviceId);
//                 if (otherSession != null && otherSession.isOpen()) {
//                     JSONObject response = new JSONObject();
//                     response.put("type", "opponent_leave");
//                     response.put("message", "对方离开了房间");
//                     sendMessage(otherSession, response.toJSONString());
//                     try { otherSession.close(); } catch (Exception e) {}
//                 }
//             }
//             // 关闭自己WebSocket
//             Session selfSession = deviceId2SessionMap_Room.get(player.deviceId);
//             if (selfSession != null && selfSession.isOpen()) {
//                 try { selfSession.close(); } catch (Exception e) {}
//             }
//         }
//         // 清理所有房间相关数据
//         // 获取房间code
//         String roomCode = null;
//         for (Map.Entry<String, Long> entry : roomCode2RoomIdMap.entrySet()) {
//             if (entry.getValue().equals(roomId)) {
//                 roomCode = entry.getKey();
//                 break;
//             }
//         }

//         // 如果找到房间code,通过cleanMap清理所有相关数据
//         if (roomCode != null) {
//             // 清理WebSocket4Game中的数据
//             WebSocket4Game.roomStataMap.remove(roomId);

//             // 清理WebSocket4Room中的数据
//             roomCode2RoomIdMap.remove(roomCode);
//             roomId2UserListMap.remove(roomId);
//             log.info("/remove deviceId2RoomIdMap delete roomId: {}, deviceId1: {},deviceId2{}", roomId, deviceId, players.stream().filter(p -> !p.deviceId.equals(deviceId)).findFirst().orElse(null).deviceId);
//             deviceId2RoomIdMap.remove(deviceId);
//             deviceId2RoomIdMap.remove(players.stream().filter(p -> !p.deviceId.equals(deviceId)).findFirst().orElse(null).deviceId);
//         }
//         // 3. 清理房间相关数据
//         WebSocket4Game.roomStataMap.remove(roomId);
//         //todo 异步执行 保存游戏记录  清空游戏记录的 map
//     }
// }


