package com.tencard.demo01;

import java.text.SimpleDateFormat;
import java.util.Date;

public class GameRoomIdGenerator {

    // 当前房间ID，从10000开始递增
    private static long currentRoomId = 1L;

    // 用于生成日期前缀的格式化器
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyMMdd");

    // 获取下一个游戏房间ID
    public static synchronized String getNextGameRoomId() {
        // 获取当前日期前缀
        String datePrefix = DATE_FORMAT.format(new Date());
        
        // 递增并获取当前房间ID
        long roomId = currentRoomId++;
        
        // 将日期前缀和房间ID组合成14位的字符串
        return String.format("%s%05d", datePrefix, roomId);
    }

    // 测试方法
    public static void main(String[] args) {
        // 打印10个新的游戏房间ID以测试
        for (int i = 0; i < 10; i++) {
            System.out.println(getNextGameRoomId());
        }
    }
}