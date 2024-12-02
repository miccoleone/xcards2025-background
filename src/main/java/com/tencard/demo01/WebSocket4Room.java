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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.tencard.demo01.GameUtil.sendMessage;


/**
 * yue 2021/8/17
 */
@Component
@ServerEndpoint("/room") // 改为room
@Slf4j
public class WebSocket4Room {

    // 房间code -> 房间Id 的映射
    public static Map<String, Long> roomCode2RoomIdMap = new ConcurrentHashMap<>(); // <4396,100007>
    // 房间Id -> List<Session> 两个玩家列表
    public static Map<Long, List<User>> roomId2UserListMap = new ConcurrentHashMap<>();


    // 设备id与会话的映射 更新保持最新
    public static Map<String,Session> deviceId2SessionMap_Room = new ConcurrentHashMap<>();
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
            // 将 deviceId 与 session 映射存储
            deviceId2SessionMap_Room.put(deviceId, session);
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
    public void onMessage(String message, Session session) throws IOException {
        log.info("/room - message内容为：{} ", message);
        User user = JSON.parseObject(message, User.class);
        if (user.deviceId == null || user.roomCode == null) return;
//        user.setNickName("xiaohua");
        user.setWinRate(49);
        user.setSession(session);
        user.setSessionId(session.getId());
        log.info("/room ======== user为：{} =========== ", user);
        if (roomCode2RoomIdMap.containsKey(user.roomCode)) { // 已经创建过房间
            // 红色方
            user.setRole(GameUtil.RoleEnum.redSide.toString());
            // 后进入游戏的玩家到场 给双发发送消息，让两人的页面都显示对方的信息
            user.setMsgCode(GameUtil.RED_JOIN_GAME); // 60
            Long roomId = roomCode2RoomIdMap.get(user.roomCode);
            deviceId2RoomIdMap.put(user.deviceId,roomId);
            roomId2UserListMap.computeIfAbsent(roomId, k -> new ArrayList<>()).add(user);
            updateBluePlayerSessionData(roomId2UserListMap,roomId);// 更新房主的session信息

            // <10001L,List<user> users>
            List<User> users = roomId2UserListMap.get(roomId);
            users.forEach(e -> {
                /**
                 * 这里的逻辑是：1.红色方的room.html接到信息，它跳转到游戏画面展示双方信息
                 *            2.蓝色方(房主)在游戏页面接收到websocket的onmessage对手信息，要显示敌方信息
                 *  */
                log.info("/room ---------- 发送消息 sessionId: {},userRole:{}", e.session.getId(), e.role);
                sendMessage(e.session, users);
            }

            );
        } else {
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

    private void updateBluePlayerSessionData(Map<Long, List<User>> roomId2UserListMap, Long roomId) {
        try{
            User red = roomId2UserListMap.get(roomId).stream().findFirst().get();
            log.info("/room ////////////// 房主 {} 之前的sessionid是 {}",roomId2UserListMap.get(roomId).stream().findFirst().get().deviceId,roomId2UserListMap.get(roomId).stream().findFirst().get().session.getId());
            red.setSession(deviceId2SessionMap_Room.get(red.deviceId));
            log.info("/room ////////////// 房主 {} 之后的sessionid是 {}",roomId2UserListMap.get(roomId).stream().findFirst().get().deviceId,roomId2UserListMap.get(roomId).stream().findFirst().get().session.getId());

        }catch (Exception e){
            log.error("/room //////////////  updateBluePlayerSessionData error : {}",e.toString());
        }
    }
}


