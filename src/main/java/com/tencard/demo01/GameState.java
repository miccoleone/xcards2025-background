package com.tencard.demo01;

import org.springframework.beans.BeanUtils;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class GameState implements Cloneable {
    private static final long serialVersionUID = 1L;

    public static final int TEN = 10;
    public static final String RED_WIN = "100";
    public static final String BLUE_WIN = "200";
    public static final String PLEASE_TAKE_CARD = "300";
    public static final String CONTINUE = "400";
    public static final String WIN_WIN = "600";

    // 消息类型常量
    public static final String MSG_TYPE_ROUND_COMPLETE = "round_complete";
    public static final String MSG_TYPE_PLEASE_TAKE_CARD = "please_take_card";
    public static final String MSG_TYPE_GAME_RESULT = "game_result";
    
    // 游戏结果常量
    public static final String RESULT_RED_WIN = "red_win";
    public static final String RESULT_BLUE_WIN = "blue_win";
    public static final String RESULT_DRAW = "draw";
    public static final String RESULT_CONTINUE = "continue";

    private int currentRound = 0;  // 当前回合数（0-9）
    private LocalDateTime startTime = LocalDateTime.now();

    // 红方的牌
    private Integer redCard1, redCard2, redCard3, redCard4, redCard5;
    private Integer redCard6, redCard7, redCard8, redCard9, redCard10;

    // 蓝方的牌
    private Integer blueCard1, blueCard2, blueCard3, blueCard4, blueCard5;
    private Integer blueCard6, blueCard7, blueCard8, blueCard9, blueCard10;

    private boolean gameCompleted = false;

    private Long roomId;  // 房间ID
    private String winner;  // 胜利者deviceId
    private String loser;   // 失败者deviceId
    private LocalDateTime gameEndTime;  // 游戏结束时间

    // 记录当前回合的出牌
    public void addCard(String role, Integer card) {
        if ("redSide".equals(role)) {
            switch (currentRound) {
                case 0: redCard1 = card; break;
                case 1: redCard2 = card; break;
                case 2: redCard3 = card; break;
                case 3: redCard4 = card; break;
                case 4: redCard5 = card; break;
                case 5: redCard6 = card; break;
                case 6: redCard7 = card; break;
                case 7: redCard8 = card; break;
                case 8: redCard9 = card; break;
                case 9: redCard10 = card; break;
            }
        } else {
            switch (currentRound) {
                case 0: blueCard1 = card; break;
                case 1: blueCard2 = card; break;
                case 2: blueCard3 = card; break;
                case 3: blueCard4 = card; break;
                case 4: blueCard5 = card; break;
                case 5: blueCard6 = card; break;
                case 6: blueCard7 = card; break;
                case 7: blueCard8 = card; break;
                case 8: blueCard9 = card; break;
                case 9: blueCard10 = card; break;
            }
        }
    }

    // 获取当前回合的牌
    public Integer getCurrentRedCard() {
        switch (currentRound) {
            case 0: return redCard1;
            case 1: return redCard2;
            case 2: return redCard3;
            case 3: return redCard4;
            case 4: return redCard5;
            case 5: return redCard6;
            case 6: return redCard7;
            case 7: return redCard8;
            case 8: return redCard9;
            case 9: return redCard10;
            default: return null;
        }
    }

    public Integer getCurrentBlueCard() {
        switch (currentRound) {
            case 0: return blueCard1;
            case 1: return blueCard2;
            case 2: return blueCard3;
            case 3: return blueCard4;
            case 4: return blueCard5;
            case 5: return blueCard6;
            case 6: return blueCard7;
            case 7: return blueCard8;
            case 8: return blueCard9;
            case 9: return blueCard10;
            default: return null;
        }
    }

    // 判断当前回合是否结束（双方都出牌了）
    public boolean isCurrentRoundComplete() {
        return getCurrentRedCard() != null && getCurrentBlueCard() != null;
    }

    // 判断当前回合结果
    public String determineRoundResult() {
        if (!isCurrentRoundComplete()) return null;

        Integer redCard = getCurrentRedCard();
        Integer blueCard = getCurrentBlueCard();

        // 如果有人出1，直接进入下一轮
        if (redCard == 1 || blueCard == 1) {
            return RESULT_CONTINUE;
        }

        // 比较点数，如果任一方是对方的2倍或以上，游戏结束
        if (redCard >= blueCard * 2) {
            gameCompleted = true;
            return RESULT_RED_WIN;
        } else if (blueCard >= redCard * 2) {
            gameCompleted = true;
            return RESULT_BLUE_WIN;
        }

        // 如果是最后一轮，比较点数
        if (currentRound >= 9) {
            gameCompleted = true;
            if (redCard > blueCard) {
                return RESULT_RED_WIN;
            } else if (blueCard > redCard) {
                return RESULT_BLUE_WIN;
            } else {
                return RESULT_DRAW;
            }
        }

        return RESULT_CONTINUE;
    }

    // 进入下一回合
    public void nextRound() {
        if (currentRound < 9) {  // 确保不会超过9（0-9共10轮）
            currentRound++;
        }
    }

    // 获取当前回合数（1-10）
    public int getRoundNumber() {
        return currentRound + 1;
    }

    // 重置游戏状态
    public void reset() {
        // 保留roomId，但重置其他游戏相关状态
        Long savedRoomId = this.roomId;
        currentRound = 0;
        gameCompleted = false;
        redCard1 = redCard2 = redCard3 = redCard4 = redCard5 = 
        redCard6 = redCard7 = redCard8 = redCard9 = redCard10 = null;
        blueCard1 = blueCard2 = blueCard3 = blueCard4 = blueCard5 = 
        blueCard6 = blueCard7 = blueCard8 = blueCard9 = blueCard10 = null;
        winner = null;
        loser = null;
        gameEndTime = null;
        startTime = LocalDateTime.now();
        this.roomId = savedRoomId;  // 恢复roomId
    }

    // 判断游戏是否结束
    public boolean isGameCompleted() {
        return gameCompleted;
    }

    // 记录游戏结果
    public void recordGameResult(String winnerDeviceId, String loserDeviceId) {
        this.winner = winnerDeviceId;
        this.loser = loserDeviceId;
        this.gameEndTime = LocalDateTime.now();
    }

    // 重写clone方法实现深拷贝
    @Override
    public GameState clone() {
        try {
            GameState cloned = (GameState) super.clone();
            // 设置新的开始时间
            cloned.startTime = LocalDateTime.now();
            return cloned;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException("Clone failed", e);
        }
    }

    // 创建一个新的GameState实例，用于记录
    public GameState createRecord() {
        GameState gameRecord = new GameState();
        BeanUtils.copyProperties(this, gameRecord);
        return gameRecord;
    }

    // Explicitly add getters to fix compilation issues
    public Long getRoomId() {
        return roomId;
    }

    public LocalDateTime getGameEndTime() {
        return gameEndTime;
    }
}
