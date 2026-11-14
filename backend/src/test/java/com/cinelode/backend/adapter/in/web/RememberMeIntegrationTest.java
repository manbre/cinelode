package com.cinelode.backend.adapter.in.web;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:cinelode-remember-test?mode=memory&cache=shared",
        "cinelode.media.root=target/classes",
        "cinelode.series.root=target/classes",
        "cinelode.security.username=test-user",
        "cinelode.security.password-hash={noop}test-password",
        "cinelode.security.dev-login-enabled=false",
        "cinelode.security.remember-me-enabled=true",
        "server.ssl.enabled=false",
        "server.address=127.0.0.1",
        "server.servlet.session.cookie.secure=true"
})
@AutoConfigureMockMvc
class RememberMeIntegrationTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void restoresAuthenticationWithoutSessionAndClearsCookieOnLogout() throws Exception {
        MvcResult csrfResult = mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk()).andReturn();
        Cookie csrf = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(csrf);
        var body = new ObjectMapper().readTree(csrfResult.getResponse().getContentAsString());
        String header = body.path("headerName").asString();
        String token = body.path("token").asString();

        MvcResult login = mvc.perform(post("/api/auth/login")
                        .cookie(csrf)
                        .header(header, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"test-user","password":"test-password"}
                                """))
                .andExpect(status().isNoContent()).andReturn();
        Cookie remember = login.getResponse().getCookie("remember-me");
        assertNotNull(remember);
        assertTrue(remember.getSecure());
        assertTrue(remember.isHttpOnly());
        assertEquals("Strict", remember.getAttribute("SameSite"));
        assertTrue(remember.getMaxAge() > 0);

        MvcResult restored = mvc.perform(get("/api/auth/session").cookie(remember))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"username\":\"test-user\"}"))
                .andReturn();
        assertNotNull(restored.getResponse().getCookie("remember-me"));
        mvc.perform(get("/api/movies").cookie(remember))
                .andExpect(status().isOk());

        MvcResult logout = mvc.perform(post("/api/auth/logout")
                        .cookie(csrf, remember)
                        .header(header, token))
                .andExpect(status().isNoContent()).andReturn();
        Cookie cleared = logout.getResponse().getCookie("remember-me");
        assertNotNull(cleared);
        assertEquals(0, cleared.getMaxAge());
        mvc.perform(get("/api/auth/session"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTamperedRememberMeCookie() throws Exception {
        mvc.perform(get("/api/auth/session").cookie(new Cookie("remember-me", "invalid")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void doesNotIssueCookieForWrongPassword() throws Exception {
        MvcResult csrfResult = mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk()).andReturn();
        var body = new ObjectMapper().readTree(csrfResult.getResponse().getContentAsString());
        MvcResult login = mvc.perform(post("/api/auth/login")
                        .cookie(csrfResult.getResponse().getCookie("XSRF-TOKEN"))
                        .header(body.path("headerName").asString(), body.path("token").asString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"test-user","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized()).andReturn();
        assertNull(login.getResponse().getCookie("remember-me"));
    }
}
