package com.tencard.demo01.saveData;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 获取所有用户
     */
    @GetMapping
    public List<UserVO> getAllUsers() {
        return userService.getAllPlayers();
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

    /**
     * 删除用户
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deletePlayer(id);
        return ResponseEntity.ok().build();
    }
}