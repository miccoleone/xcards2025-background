package com.tencard.demo01.saveData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private UserRepository userRepository;

    /**
     * 根据 openId 查找用户，如果不存在则创建新用户。
     * @param openId 用户的唯一标识
     * @return 找到或创建的用户实体
     */
    public UserVO findOrCreateUserByOpenId(String openId) {
        UserVO user = userRepository.findByOpenId(openId);
        if (user == null) {
            log.info("Creating new user for openId: {}", openId);
            user = new UserVO();
            user.setOpenId(openId);
            // 设置新用户的默认值
            user.setWins(0);
            user.setLosses(0);
            user.setBean(1000L); // 新用户注册时给予1000豆子
            user.setTotalGames(0);
            user = userRepository.save(user);
        }
        return user;
    }

    /**
     * 更新用户的昵称。
     * @param openId 用户的唯一标识
     * @param nickname 新的昵称
     * @return 更新后的用户实体，如果用户不存在则创建新用户
     */
    public UserVO updateUserNickname(String openId, String nickname) {
        if(!StringUtils.hasLength(openId) || !StringUtils.hasLength(nickname) || nickname.length() > 20) {
            return null;
        }
        UserVO user = userRepository.findByOpenId(openId);
        if (user != null) {
            log.info("Updating nickname for openId: {} to {}", openId, nickname);
            user.setNickName(nickname);
            user = userRepository.save(user);
        } else {
            // 新建
            user = new UserVO();
            user.setOpenId(openId);
            user.setNickName(nickname);
            // 设置新用户的默认值
            user.setWins(0);
            user.setLosses(0);
            user.setBean(1000L); // 新用户注册时给予1000豆子
            user.setTotalGames(0);
            user = userRepository.save(user);
        }
        return user;
    }

    /**
     * 更新用户战绩
     * @param openId 用户的唯一标识
     * @param isWin 是否胜利
     */
    public void updateUserStats(String openId, boolean isWin) {
        UserVO user = userRepository.findByOpenId(openId);
        if (user != null) {
            if (isWin) {
                user.setWins(user.getWins() + 1);
            } else {
                user.setLosses(user.getLosses() + 1);
            }
            user.setTotalGames(user.getTotalGames() + 1); // 增加总游戏场次计数
            userRepository.save(user);
            log.info("Updated stats for user {}: wins={}, losses={}", openId, user.getWins(), user.getLosses());
        } else {
            log.warn("Attempted to update stats for non-existent user with openId: {}", openId);
        }
    }

    /**
     * 更新用户的豆子数量
     * @param openId 用户的唯一标识
     * @param amount 要增加或减少的豆子数量（可以为负数）
     */
    public void updateUserBean(String openId, long amount) {
        UserVO user = userRepository.findByOpenId(openId);
        if (user != null) {
            user.setBean(user.getBean() + amount);
            userRepository.save(user);
            log.info("Updated bean for user {}: new balance is {}", openId, user.getBean());
        } else {
            log.warn("Attempted to update bean for non-existent user with openId: {}", openId);
        }
    }

    /**
     * 获取排行榜
     * @return 按胜利场次排序的用户列表 (Top 10)
     */
    public List<UserVO> getLeaderboard() {
        return userRepository.findTop10ByOrderByWinsDesc();
    }

    public List<UserVO> getAllPlayers() {
        return userRepository.findAll();
    }

    public UserVO savePlayer(UserVO user) {
        return userRepository.save(user);
    }

    public void deletePlayer(Long id) {
        userRepository.deleteById(id);
    }

    public UserVO createPlayer(String openId, String nickName) {
        if (userRepository.findByOpenId(openId) != null) {
            return null; // Or handle as an update/error
        }
        UserVO user = new UserVO();
        user.setOpenId(openId);
        user.setNickName(nickName);
        user.setWins(0);
        user.setLosses(0);
        return userRepository.save(user);
    }

    public UserVO createRandomPlayer(String openId) {
        UserVO existingUser = userRepository.findByOpenId(openId);
        if (existingUser != null) {
            return existingUser;
        }
        UserVO user = new UserVO();
        user.setOpenId(openId);
        user.setNickName("Player_" + (System.currentTimeMillis() % 10000));
        user.setWins(0);
        user.setLosses(0);
        return userRepository.save(user);
    }
}
