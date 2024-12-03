package com.tencard.demo01;

import lombok.Data;

@Data
public class GameState {
    private static final long serialVersionUID = 1L;

    public Integer m = 0;
    public Integer otherCard;

    public void reset() {
        // 重置游戏状态
        m = 0;
        otherCard = null;
    }
}
