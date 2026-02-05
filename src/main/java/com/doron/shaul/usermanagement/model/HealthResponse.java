package com.doron.shaul.usermanagement.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class HealthResponse {
    private String status;
    private LocalDateTime timestamp;
    private String application;
}
