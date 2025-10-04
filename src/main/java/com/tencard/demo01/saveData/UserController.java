package com.tencard.demo01.saveData;

import com.alibaba.fastjson.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/user") // 路径改为单数
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/signin/{openId}")
    public ResponseEntity<?> signIn(@PathVariable String openId) {
        JSONObject json = new JSONObject();
        json.put("success", false);

        if (openId == null || openId.trim().isEmpty()) {
            json.put("message", "openId is required");
            return ResponseEntity.ok(json);
        }

        UserVO user = userService.findOrCreateUserByOpenId(openId);
        LocalDate today = LocalDate.now();
        if(user == null) {
            json.put("message", "用户不存在");
            return ResponseEntity.ok(json);
        }

        if (user.getLastSignInDate() != null && user.getLastSignInDate().isEqual(today)) {
            json.put("message", "您今天已经签到过了");
            return ResponseEntity.ok(json);
        }

        user.setBean(user.getBean() + 500);
        user.setLastSignInDate(today);
        UserVO updatedUser = userService.savePlayer(user);

        json.put("success", true);
        json.put("message", "签到成功，获得500豆子！");
        json.put("beans", updatedUser.getBean());
        return ResponseEntity.ok(json);
    }

    /**
     * 获取排行榜数据
     * @return 按胜利场次排序的用户列表 (Top 10)
     */
    @GetMapping("/leaderboard")
    public List<UserVO> getLeaderboard() {
        return userService.getLeaderboard();
    }

    /**
     * 创建一个新用户
     */
    @PostMapping
    public ResponseEntity<UserVO> createUser(@RequestBody UserVO user) {
        if (user.getOpenId() == null || user.getOpenId().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        UserVO createdUser = userService.createPlayer(user.getOpenId(), user.getNickName());
        if (createdUser == null) {
            return ResponseEntity.status(409).build(); // 409 Conflict - User already exists
        }
        return ResponseEntity.ok(createdUser);
    }

    /**
     * 更新用户信息
     */
    @PutMapping("/{id}")
    public ResponseEntity<UserVO> updateUser(@PathVariable Long id, @RequestBody UserVO userDetails) {
        // A more robust implementation would fetch user by ID directly
        UserVO existingUser = userService.getAllPlayers().stream()
                .filter(u -> u.getId().equals(id))
                .findFirst()
                .orElse(null);

        if (existingUser == null) {
            return ResponseEntity.notFound().build();
        }

        existingUser.setNickName(userDetails.getNickName());
        existingUser.setWins(userDetails.getWins());
        existingUser.setLosses(userDetails.getLosses());

        UserVO updatedUser = userService.savePlayer(existingUser);
        return ResponseEntity.ok(updatedUser);
    }

    @GetMapping("/{openId}")
    public ResponseEntity<UserVO> getUserInfo(@PathVariable String openId) {
        if (openId == null || openId.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        UserVO user = userService.findOrCreateUserByOpenId(openId);
        return ResponseEntity.ok(user);
    }

    /**
     * 更新用户昵称
     * @param userVO 包含 openId 和新的 nickName
     * @return 更新结果
     */
    @PostMapping("/updateNickname")
    public ResponseEntity<?> updateNickname(@RequestBody UserVO userVO) {
        String openId = userVO.getOpenId();
        String nickname = userVO.getNickName();
        if(!StringUtils.hasLength(openId) || !StringUtils.hasLength(nickname) || nickname.length() > 20) {
            return ResponseEntity.badRequest().build();
        }
        final UserVO vo = userService.updateUserNickname(openId, nickname);
        return ResponseEntity.ok(vo);
    }
}