package com.tencard.demo01.saveData;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<UserVO, Long> {

    UserVO findByOpenId(String openId);

    /**
     * 查询胜利场次排名前10的用户
     * @return 用户列表
     */
    List<UserVO> findTop10ByOrderByWinsDesc();
}
