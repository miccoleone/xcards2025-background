package com.tencard.demo01.saveData;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/players")
public class PlayerController {

    @Autowired
    private PlayerService playerService;
    @Autowired
    private PlayerRepository playerRepository;

    @GetMapping
    public List<Player> getAllPlayers() {
        return playerService.getAllPlayers();
    }

    @GetMapping("/insertTestData")
    public Object insertTestData() {
        for (int i = 0; i < 10; i++) {
            Player player = new Player(null, "deviceId" + i, "nickName" + i, 0, 0);
            playerService.savePlayer(player);
        }
        return ResponseEntity.status(HttpStatus.OK);
    }

    @GetMapping("/{username}")
    public Player getPlayerByUsername(@PathVariable String username) {
        return playerService.createRandomPlayer(username); // 用于测试创建数据
    }

    @PostMapping
    public Player createPlayer(@RequestBody Player player) {
        return playerService.savePlayer(player);
    }

    @PostMapping("/create")
    public ResponseEntity<Player> createPlayer(@RequestBody Map<String, String> request) {
        String deviceId = request.get("deviceId");
        String nickName = request.get("nickName");

        if (deviceId == null || nickName == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Player player = playerService.createPlayer(deviceId, nickName);
            return ResponseEntity.ok(player);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/{id}")
    public Player updatePlayer(@PathVariable Long id, @RequestBody Player playerDetails) {
        Player player = playerService.findByDeviceId(playerDetails.getDeviceId());
        player.setNickName(playerDetails.getNickName());
        player.setWins(playerDetails.getWins());
        player.setLosses(playerDetails.getLosses());
        return playerService.savePlayer(player);
    }

    @DeleteMapping("/{id}")
    public void deletePlayer(@PathVariable Long id) {
        playerService.deletePlayer(id);
    }
}