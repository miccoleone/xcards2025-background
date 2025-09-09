package com.tencard.demo01.saveData;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/players")
public class UserController {

    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;

    @GetMapping
    public List<UserVO> getAllPlayers() {
        return userService.getAllPlayers();
    }

    @GetMapping("/insertTestData")
    public Object insertTestData() {
        for (int i = 0; i < 10; i++) {
            UserVO userVO = new UserVO(null, "deviceId" + i, "nickName" + i, 0, 0);
            userService.savePlayer(userVO);
        }
        return ResponseEntity.status(HttpStatus.OK);
    }

    @GetMapping("/{username}")
    public UserVO getPlayerByUsername(@PathVariable String username) {
        return userService.createRandomPlayer(username); // 用于测试创建数据
    }

    @PostMapping
    public UserVO createPlayer(@RequestBody UserVO userVO) {
        return userService.savePlayer(userVO);
    }

    @PostMapping("/create")
    public ResponseEntity<UserVO> createPlayer(@RequestBody Map<String, String> request) {
        String deviceId = request.get("deviceId");
        String nickName = request.get("nickName");

        if (deviceId == null || nickName == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            UserVO userVO = userService.createPlayer(deviceId, nickName);
            return ResponseEntity.ok(userVO);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/{id}")
    public UserVO updatePlayer(@PathVariable Long id, @RequestBody UserVO userVODetails) {
        UserVO userVO = userService.findByDeviceId(userVODetails.getDeviceId());
        userVO.setNickName(userVODetails.getNickName());
        userVO.setWins(userVODetails.getWins());
        userVO.setLosses(userVODetails.getLosses());
        return userService.savePlayer(userVO);
    }

    @DeleteMapping("/{id}")
    public void deletePlayer(@PathVariable Long id) {
        userService.deletePlayer(id);
    }
}