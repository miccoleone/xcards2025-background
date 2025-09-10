package com.tencard.demo01;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api")
public class NicknameController {

    @Value("${wechat.appid}")
    private String appId;

    @Value("${wechat.secret}")
    private String appSecret;
    
    // 本地敏感词检查
    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
        "(管理员|客服|草|艹)",
        Pattern.CASE_INSENSITIVE
    );

    @PostMapping("/check-nickname")
    public ResponseEntity<Map<String, Object>> checkNickname(@RequestBody Map<String, String> request) {
        String nickname = request.get("nickname");
        String openId = request.get("openId");

        System.out.println("🔥🔥🔥 检查昵称: " + nickname);
        System.out.println("🔥🔥🔥 OpenID: " + openId);
        System.out.println("🔥🔥🔥 使用AppID: " + appId);
        System.out.println("🔥🔥🔥 使用Secret: " + appSecret);
        
        Map<String, Object> response = new HashMap<>();
        
        // 先进行本地敏感词检查
        if (SENSITIVE_PATTERN.matcher(nickname).find()) {
            System.out.println("❌ 本地敏感词检查失败: " + nickname);
            response.put("success", false);
            response.put("message", "昵称包含敏感词，请重新输入");
            System.out.println("🔥🔥🔥 返回结果: " + response);
            return ResponseEntity.ok(response);
        }
        
        System.out.println("✅ 本地敏感词检查通过，开始调用微信API");
        
        try {
            RestTemplate restTemplate = new RestTemplate();
            
            // 获取access_token
            String tokenUrl = String.format(
                    "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=%s&secret=%s",
                    appId, appSecret
            );
            
            System.out.println("🌐 Token请求URL: " + tokenUrl);
            Map tokenData = restTemplate.getForObject(tokenUrl, Map.class);
            System.out.println("🎫 Token响应: " + tokenData);
            
            if (tokenData != null && tokenData.containsKey("access_token")) {
                String accessToken = (String) tokenData.get("access_token");
                System.out.println("✅ 获取到access_token: " + accessToken.substring(0, 20) + "...");
                
                // 调用内容安全检查API
                String checkUrl = "https://api.weixin.qq.com/wxa/msg_sec_check?access_token=" + accessToken;
                System.out.println("🔍 内容检查URL: " + checkUrl);
                
                Map<String, Object> checkRequest = new HashMap<>();
                checkRequest.put("content", nickname);
                checkRequest.put("version", 2);
                checkRequest.put("scene", 1);
                checkRequest.put("openid", openId);

                System.out.println("📝 内容检查请求: " + checkRequest);
                Map checkResult = restTemplate.postForObject(checkUrl, checkRequest, Map.class);
                System.out.println("🎯 内容检查响应: " + checkResult);
                
                if (checkResult != null) {
                    Integer errcode = (Integer) checkResult.get("errcode");
                    String errmsg = (String) checkResult.get("errmsg");
                    System.out.println("📊 错误码: " + errcode + ", 错误信息: " + errmsg);
                    
                    if (errcode == 0) {
                        // 检查result字段
                        Map result = (Map) checkResult.get("result");
                        if (result != null) {
                            String suggest = (String) result.get("suggest");
                            Integer label = (Integer) result.get("label");
                            System.out.println("🎯 检查结果 - suggest: " + suggest + ", label: " + label);
                            
                            if ("pass".equals(suggest)) {
                                System.out.println("✅ 微信API检查通过");
                                response.put("success", true);
                                response.put("message", "昵称检查通过");
                            } else if ("risky".equals(suggest)) {
                                System.out.println("❌ 微信API检查失败，内容违规");
                                response.put("success", false);
                                response.put("message", "昵称包含违规内容，请重新输入");
                            } else if ("review".equals(suggest)) {
                                System.out.println("⚠️ 需要人工审核，暂时通过");
                                response.put("success", true);
                                response.put("message", "昵称检查通过");
                            } else {
                                System.out.println("⚠️ 未知suggest值，降级处理: " + suggest);
                                response.put("success", true);
                                response.put("message", "昵称检查通过（降级处理）");
                            }
                        } else {
                            System.out.println("❌ result字段为空");
                            response.put("success", true);
                            response.put("message", "昵称检查通过（降级处理）");
                        }
                    } else {
                        System.out.println("❌ API调用失败，错误码: " + errcode);
                        response.put("success", true);
                        response.put("message", "昵称检查通过（降级处理）");
                    }
                } else {
                    System.out.println("❌ 内容检查响应为空");
                    response.put("success", true);
                    response.put("message", "昵称检查通过（降级处理）");
                }
            } else {
                System.out.println("❌ 获取access_token失败: " + tokenData);
                response.put("success", true);
                response.put("message", "昵称检查通过（降级处理）");
            }
        } catch (Exception e) {
            System.out.println("💥 微信API调用异常: " + e.getMessage());
            e.printStackTrace();
            response.put("success", true);
            response.put("message", "昵称检查通过（降级处理）");
        }
        
        System.out.println("🔥🔥🔥 最终返回结果: " + response);
        return ResponseEntity.ok(response);
    }
}
