package jaemin.ai.ratelimiter.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = {
        "rate-limit.storage-type=IN_MEMORY",
        "spring.autoconfigure.exclude=org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration"
})
class DemoControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("ping 엔드포인트가 정상 응답한다")
    void pingReturns200() throws Exception {
        mockMvc.perform(get("/api/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("pong"))
                .andExpect(header().exists("X-RateLimit-Remaining"));
    }

    @Test
    @DisplayName("rate limit 초과 시 429를 반환한다")
    void returns429WhenLimitExceeded() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/ping")).andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/ping"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error").value("Too Many Requests"));
    }

    @Test
    @DisplayName("strict 엔드포인트는 2회 초과 시 429를 반환한다")
    void strictEndpointLimitsTo2() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/api/strict")).andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/strict"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("설정 조회 API가 정상 동작한다")
    void configEndpointWorks() throws Exception {
        mockMvc.perform(get("/api/rate-limit/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.defaultAlgorithm").value("TOKEN_BUCKET"));
    }
}
