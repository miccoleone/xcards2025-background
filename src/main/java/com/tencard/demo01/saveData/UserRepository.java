package com.tencard.demo01.saveData;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<UserVO, Long> {

    UserVO findByDeviceId(String deviceId);
}