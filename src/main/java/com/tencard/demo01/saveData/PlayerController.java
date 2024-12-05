package com.tencard.demo01.saveData;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/players")
public class PlayerController {

    @Autowired
    private PlayerService playerService;

    @GetMapping
    public List<Player> getAllPlayers() {
        return playerService.getAllPlayers();
    }

    @GetMapping("/{username}")
    public Player getPlayerByUsername(@PathVariable String username) {
        return playerService.createRandomPlayer(username); // 用于测试创建数据
    }

    @PostMapping
    public Player createPlayer(@RequestBody Player player) {
        return playerService.savePlayer(player);
    }

    @PutMapping("/{id}")
    public Player updatePlayer(@PathVariable Long id, @RequestBody Player playerDetails) {
        Player player = playerService.getPlayerByUsername(playerDetails.getUsername());
        player.setUsername(playerDetails.getUsername());
        player.setWins(playerDetails.getWins());
        player.setLosses(playerDetails.getLosses());
        return playerService.savePlayer(player);
    }

    @DeleteMapping("/{id}")
    public void deletePlayer(@PathVariable Long id) {
        playerService.deletePlayer(id);
    }
}