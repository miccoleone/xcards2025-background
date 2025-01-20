package com.tencard.demo01.saveData;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;
import java.util.Optional;

@Slf4j
@Service
public class PlayerService {

    @Autowired
    private PlayerRepository playerRepository;

    public List<Player> getAllPlayers() {
        return playerRepository.findAll();
    }

    public Player findByDeviceId(String deviceId) {
        return playerRepository.findByDeviceId(deviceId);
    }

    /**
     * 随机创建一个玩家，并返回该玩家的信息。
     */
    public Player createRandomPlayer(String deviceId) {
        final Player byDeviceId = playerRepository.findByDeviceId(deviceId);
        // 生成随机的中文名字
        String randomUsername = generateRandomChineseName(2);  // 生成2个汉字的名字

        // 生成随机的胜利场次和失败场次（1到100之间）
        Random random = new Random();
        int wins = 0;
        int losses = 0;

        // 创建玩家对象
        Player player = new Player();
        player.setDeviceId(deviceId);
        player.setNickName(randomUsername);
        player.setWins(wins);
        player.setLosses(losses);

        // 保存玩家到数据库
        Player savedPlayer = playerRepository.save(player);
        log.info("新建了一条数据 player: {}", savedPlayer);

        // 返回查询结果
        return playerRepository.findByDeviceId(deviceId);
    }

    /**
     * 生成指定长度的随机中文名字。
     *
     * @param length 名字的长度（汉字数量）
     * @return 随机生成的中文名字
     */
    private String generateRandomChineseName(int length) {
        StringBuilder name = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            // Unicode范围内的汉字：0x4E00 到 0x9FA5
            char randomChar = (char) (0x4E00 + random.nextInt(0x9FA5 - 0x4E00 + 1));
            name.append(randomChar);
        }
        return name.toString();
    }

    public Player savePlayer(Player player) {
        return playerRepository.save(player);
    }

    public void deletePlayer(Long id) {
        playerRepository.deleteById(id);
    }

    // 新增: 创建用户方法
    public Player createPlayer(String deviceId, String nickName) {
        // 检查是否已存在
        Player player1 = playerRepository.findByDeviceId(deviceId);
        if (player1 != null) {
            log.info("用户已经存在");
            return player1;
        }

        // 创建新用户
        Player player = new Player();
        player.setDeviceId(deviceId);
        player.setNickName(nickName);
        player.setWins(0);
        player.setLosses(0);
        
        return playerRepository.save(player);
    }

    // 其他业务逻辑方法
}