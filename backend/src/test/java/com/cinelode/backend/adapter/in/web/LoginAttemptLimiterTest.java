package com.cinelode.backend.adapter.in.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptLimiterTest {
    @Test
    void blocksAdditionalAttemptsAfterFiveUntilReset() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter();

        for (int attempt = 0; attempt < 5; attempt++) {
            assertTrue(limiter.tryAcquire("192.168.1.20"));
        }
        assertFalse(limiter.tryAcquire("192.168.1.20"));
        assertTrue(limiter.tryAcquire("192.168.1.21"));

        limiter.reset("192.168.1.20");
        assertTrue(limiter.tryAcquire("192.168.1.20"));
    }
}
