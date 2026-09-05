package com.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import java.util.Map;

@Service
public class SocialLoginService {

    private final RestTemplate restTemplate = new RestTemplate();

    public String verifyGoogleTokenAndGetEmail(String accessToken) throws Exception {
        String url = "https://www.googleapis.com/oauth2/v3/userinfo";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
        
        if (response.getBody() != null && response.getBody().containsKey("email")) {
            return (String) response.getBody().get("email");
        }
        throw new RuntimeException("Could not extract email from Google token.");
    }

    public String verifyFacebookTokenAndGetEmail(String accessToken) throws Exception {
        String url = "https://graph.facebook.com/me?fields=email&access_token=" + accessToken;
        Map response = restTemplate.getForObject(url, Map.class);
        if (response != null && response.containsKey("email")) {
            return (String) response.get("email");
        }
        throw new RuntimeException("Could not extract email from Facebook token.");
    }
}
