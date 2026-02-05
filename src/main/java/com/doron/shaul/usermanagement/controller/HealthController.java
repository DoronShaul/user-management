package com.doron.shaul.usermanagement.controller;

import com.doron.shaul.usermanagement.model.HealthResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<HealthResponse> getHealth() {
        HealthResponse health = new HealthResponse("UP", LocalDateTime.now(), "user-management");
        return ResponseEntity.ok(health);
    }
}
