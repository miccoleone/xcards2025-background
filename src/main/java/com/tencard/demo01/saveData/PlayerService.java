package com.tencard.demo01.saveData;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

@Slf4j
@Service
public class PlayerService {

    @Autowired
    private PlayerRepository playerRepository;

    public List<Player> getAllPlayers() {
        return playerRepository.findAll();
    }

    public Player getPlayerByUsername(String username) {
        return playerRepository.findByUsername(username);
    }

    /**
     * 随机创建一个玩家，并返回该玩家的信息。
     */
    public Player createRandomPlayer(String s) {
        // 生成随机的中文名字
        String randomUsername = generateRandomChineseName(2);  // 生成2个汉字的名字

        // 生成随机的胜利场次和失败场次（1到100之间）
        Random random = new Random();
        int wins = random.nextInt(100) + 1;  // 1到100之间的随机整数
        int losses = random.nextInt(100) + 1;  // 1到100之间的随机整数

        // 创建玩家对象
        Player player = new Player();
        player.setUsername(randomUsername);
        player.setWins(wins);
        player.setLosses(losses);

        // 保存玩家到数据库
        Player savedPlayer = playerRepository.save(player);
        log.info("新建了一条数据 player: {}", savedPlayer);

        // 返回查询结果
        return playerRepository.findByUsername(randomUsername);
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

    // 其他业务逻辑方法
}