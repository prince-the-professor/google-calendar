package org.upwork.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.upwork.service.CalendarService;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/auth/google")
public class GoogleOAuthController {

    @Value("${google.client.id}")
    private String clientId;

    @Value("${google.redirect.uri}")
    private String redirectUri;

    private final CalendarService calendarService;

    @Autowired
    public GoogleOAuthController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    private static final String AUTH_BASE_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String SCOPE = "https://www.googleapis.com/auth/calendar https://www.googleapis.com/auth/gmail.send";

    @GetMapping("/login")
    public ResponseEntity<String> getAuthUrl(@RequestParam String doctorId) {
        String authUrl = AUTH_BASE_URL +
                "?client_id=" + clientId +
                "&redirect_uri=" + redirectUri +
                "&response_type=code" +
                "&scope=" + SCOPE +
                "&access_type=offline" +
                "&prompt=consent" +
                "&state=" + doctorId;

        return ResponseEntity.ok(authUrl);
    }

    @GetMapping("/callback")
    public ResponseEntity<String> oauthCallback(@RequestParam String code, @RequestParam String state) {
        // 'state' holds doctorId
        String doctorId = state;
        String response = calendarService.registerDoctor(code, doctorId);
        return ResponseEntity.ok(response);
    }

}