package com.tencard.demo01.saveData;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlayerRepository extends JpaRepository<Player, Long> {
    // 可以添加自定义查询方法
    Player findByUsername(String username);
}