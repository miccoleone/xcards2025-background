package com.tencard.demo01.saveData;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Entity
@Table(name = "players")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Player {

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
    private String deviceId;

    @Column(nullable = false, unique = false)
    private String nickName;

    @Column(nullable = false)
    private Integer wins;

    @Column(nullable = false)
    private Integer losses;

}