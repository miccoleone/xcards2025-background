package com.tencard.demo01.saveData;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserVO {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "player_seq")
    @SequenceGenerator(
            name = "player_seq",
            sequenceName = "player_sequence",
            initialValue = 10000,  // 设置初始值为10000
            allocationSize = 1    // 每次递增1
    )
    private Long id;

    @Column(nullable = false, unique = true)
    private String openId;

    @Column
    private String nickName;

    @Column
    private Integer wins;

    @Column
    private Integer losses;

    @Column(name = "create_time", updatable = false)
    @CreationTimestamp
    private java.time.LocalDateTime createTime;

    @Column
    private Long bean;

    @Column
    private java.time.LocalDate lastSignInDate;

    @Column
    private Integer totalGames;

}
