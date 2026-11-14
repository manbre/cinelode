package com.cinelode.backend.adapter.in.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigurationTest {
    @Test
    void explainsWhereToConfigureRequiredPasswordHash() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new SecurityConfiguration().userDetailsService("cinelode", ""));

        assertTrue(exception.getMessage().contains("backend/.env"));
    }
}
