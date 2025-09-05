package com.tencard.demo01;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 页面路由控制：
 * - /xcards -> 转发到静态资源 xcards.html（便于以 /xcards 访问你的小游戏）
 * 说明：index.html 将直接由 Spring Boot 静态资源机制在 "/" 提供，无需额外控制器。
 */
@Controller
public class PageController {

    @GetMapping("/xcards")
    public String xcards() {
        // 使用 forward 而非 redirect，避免再次请求
        return "forward:/xcards.html";
    }
}