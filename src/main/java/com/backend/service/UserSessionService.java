package com.backend.service;

import com.backend.model.User;
import com.backend.model.UserSession;
import com.backend.repository.UserSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserSessionService {

    private final UserSessionRepository userSessionRepository;

    public void createSession(User user, String token, HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        String ipAddress = getClientIp(request);
        
        String os = parseOS(userAgent);
        String browser = parseBrowser(userAgent);
        String location = getLocationFromIp(ipAddress);

        UserSession session = UserSession.builder()
                .user(user)
                .token(token)
                .deviceOS(os)
                .browser(browser)
                .ipAddress(ipAddress)
                .location(location)
                .build();
                
        userSessionRepository.save(session);
    }

    private String getClientIp(HttpServletRequest request) {
        String[] headers = {
            "X-Forwarded-For",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "HTTP_VIA",
            "REMOTE_ADDR"
        };
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && ip.length() != 0 && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private String parseOS(String userAgent) {
        if (userAgent == null) return "Unknown OS";
        if (userAgent.toLowerCase().contains("windows")) return "Windows";
        if (userAgent.toLowerCase().contains("mac")) return "Mac OS";
        if (userAgent.toLowerCase().contains("x11") || userAgent.toLowerCase().contains("linux")) return "Linux";
        if (userAgent.toLowerCase().contains("android")) return "Android";
        if (userAgent.toLowerCase().contains("iphone") || userAgent.toLowerCase().contains("ipad")) return "iOS";
        return "Unknown OS";
    }

    private String parseBrowser(String userAgent) {
        if (userAgent == null) return "Unknown Browser";
        String lowerAgent = userAgent.toLowerCase();
        if (lowerAgent.contains("edg")) return "Edge";
        if (lowerAgent.contains("chrome") && !lowerAgent.contains("chromium")) return "Chrome";
        if (lowerAgent.contains("safari") && !lowerAgent.contains("chrome")) return "Safari";
        if (lowerAgent.contains("firefox")) return "Firefox";
        if (lowerAgent.contains("opera") || lowerAgent.contains("opr")) return "Opera";
        return "Unknown Browser";
    }

    private String getLocationFromIp(String ip) {
        if (ip.equals("0:0:0:0:0:0:0:1") || ip.equals("127.0.0.1") || ip.startsWith("192.168.")) {
            return "Local Network";
        }
        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = "http://ip-api.com/json/" + ip + "?fields=city,country";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            Map<String, Object> data = response.getBody();
            if (data != null && data.containsKey("city") && data.containsKey("country")) {
                return data.get("city") + ", " + data.get("country");
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch location for IP: " + ip);
        }
        return "Unknown Location";
    }
}
