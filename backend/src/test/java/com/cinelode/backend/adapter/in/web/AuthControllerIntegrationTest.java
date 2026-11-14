package com.cinelode.backend.adapter.in.web;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:cinelode-auth-test?mode=memory&cache=shared",
        "cinelode.media.root=target/classes",
        "cinelode.series.root=target/classes",
        "cinelode.security.username=test-user",
        "cinelode.security.password-hash={noop}test-password",
        "cinelode.security.dev-login-enabled=true",
        "cinelode.security.remember-me-enabled=true",
        "server.ssl.enabled=false",
        "server.address=127.0.0.1",
        "server.servlet.session.cookie.secure=false"
})
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {
    @Autowired
    private MockMvc mvc;

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/movies",
            "/api/movies/1",
            "/api/movies/1/poster",
            "/api/movies/1/external-url",
            "/api/movies/1/tracks",
            "/api/movies/1/duration",
            "/api/movies/1/play",
            "/api/movies/1/stream",
            "/api/series",
            "/api/series/seasons/1/poster",
            "/api/series/episodes/1/tracks",
            "/api/series/episodes/1/duration",
            "/api/series/episodes/1/play",
            "/api/auth/session"
    })
    void requiresAuthenticationForEveryCatalogAndMediaEndpoint(String path) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rotatesSessionIdAndAllowsAuthenticatedBrowserMediaRequests() throws Exception {
        CsrfState csrf = fetchCsrfToken();
        MockHttpSession priorSession = new MockHttpSession();
        String oldSessionId = priorSession.getId();

        MvcResult result = mvc.perform(post("/api/auth/login")
                        .session(priorSession)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"test-user","password":"test-password"}
                                """))
                .andExpect(status().isNoContent())
                .andReturn();
        assertNotEquals(oldSessionId, priorSession.getId());
        assertNull(result.getResponse().getCookie("remember-me"));

        mvc.perform(get("/api/auth/session").session(priorSession))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"username\":\"test-user\"}"));
        mvc.perform(get("/api/movies").session(priorSession))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsWrongPasswordsAndStateChangesWithoutCsrf() throws Exception {
        CsrfState csrf = fetchCsrfToken();

        mvc.perform(post("/api/auth/login")
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"test-user","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/auth/logout"))
                .andExpect(status().isForbidden());
    }

    @Test
    void devLoginCreatesAnAuthenticatedSessionOnlyWithLocalProxyMarkerAndCsrf() throws Exception {
        CsrfState csrf = fetchCsrfToken();
        MockHttpSession session = new MockHttpSession();

        mvc.perform(post("/api/auth/dev-login")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .header("X-Cinelode-Dev-Login", "true"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/session").session(session))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"username\":\"test-user\"}"));
        mvc.perform(post("/api/auth/dev-login")
                        .header("X-Cinelode-Dev-Login", "false"))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutInvalidatesTheSessionAndCsrfCookieIsNotHttpOnly() throws Exception {
        CsrfState csrf = fetchCsrfToken();
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/auth/login")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"test-user","password":"test-password"}
                                """))
                .andExpect(status().isNoContent())
                .andReturn();
        assertFalse(csrf.cookie().isHttpOnly());

        mvc.perform(post("/api/auth/logout")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isNoContent());
        assertTrue(session.isInvalid());
        mvc.perform(get("/api/auth/session"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void doesNotAllowCrossOriginBrowserAccess() throws Exception {
        mvc.perform(get("/api/movies").header("Origin", "https://untrusted.example"))
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertNull(result.getResponse().getHeader("Access-Control-Allow-Origin")));
    }

    private CsrfState fetchCsrfToken() throws Exception {
        MvcResult result = mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(cookie);
        var responseBody = new ObjectMapper().readTree(result.getResponse().getContentAsString());
        String token = responseBody.path("token").asString();
        String headerName = responseBody.path("headerName").asString();
        return new CsrfState(cookie, token, headerName);
    }

    private record CsrfState(Cookie cookie, String token, String headerName) {}
}
