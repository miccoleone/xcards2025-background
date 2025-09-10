package com.tencard.demo01.saveData;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/wx")
@Slf4j
public class WxLoginController {

    @Autowired
    private UserService userService;

    // 您需要将小程序的 appId 和 appSecret 添加到 application.properties 文件中
    // 例如:
    // wx.appId=your_app_id
    // wx.appSecret=your_app_secret
    @Value("${wechat.appid}")
    private String appId;

    @Value("${wechat.secret}")
    private String appSecret;

    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/login")
    public Map<String, Object> wxLogin(@RequestParam("code") String code) {
        String url = "https://api.weixin.qq.com/sns/jscode2session" +
                "?appid=" + appId +
                "&secret=" + appSecret +
                "&js_code=" + code +
                "&grant_type=authorization_code";

        String response = restTemplate.getForObject(url, String.class);
        JSONObject jsonResponse = JSONObject.parseObject(response);

        Map<String, Object> result = new HashMap<>();
        String openId = jsonResponse.getString("openid");

        if (openId != null) {
            // 根据 openId 查找或创建用户
            UserVO user = userService.findOrCreateUserByOpenId(openId);
            log.info("------ 查询｜创建的用户 : {} ---------", user.toString());
            result.put("success", true);
            result.put("openId", user.getOpenId());
        } else {
            result.put("success", false);
            result.put("message", jsonResponse.getString("errmsg"));
        }
        return result;
    }
}