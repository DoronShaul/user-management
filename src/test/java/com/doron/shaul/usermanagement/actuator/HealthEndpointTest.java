package com.doron.shaul.usermanagement.actuator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class HealthEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpoint_ApplicationHealthy_Returns200() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists());
    }

    @Test
    void healthEndpoint_ResponseIncludesOverallStatus_ReturnsStatusField() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").isNotEmpty())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void healthEndpoint_ComponentDetailsNotExposed_ReturnsOnlyStatus() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.components").doesNotExist());
    }
}
