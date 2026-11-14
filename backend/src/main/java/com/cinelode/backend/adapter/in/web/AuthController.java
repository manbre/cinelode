package com.cinelode.backend.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** Handles browser login and issues the CSRF token used by state-changing requests. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final UserDetailsService userDetailsService;
    private final TokenBasedRememberMeServices rememberMeServices;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final ClientAddressResolver clientAddressResolver;
    private final String username;
    private final boolean devLoginEnabled;
    private final boolean secureCookies;
    private final boolean rememberMeEnabled;

    public AuthController(
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            UserDetailsService userDetailsService,
            TokenBasedRememberMeServices rememberMeServices,
            LoginAttemptLimiter loginAttemptLimiter,
            ClientAddressResolver clientAddressResolver,
            @Value("${cinelode.security.username:cinelode}") String username,
            @Value("${cinelode.security.dev-login-enabled:false}") boolean devLoginEnabled,
            @Value("${server.servlet.session.cookie.secure:true}") boolean secureCookies,
            @Value("${cinelode.security.remember-me-enabled:false}") boolean rememberMeEnabled) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.userDetailsService = userDetailsService;
        this.rememberMeServices = rememberMeServices;
        this.loginAttemptLimiter = loginAttemptLimiter;
        this.clientAddressResolver = clientAddressResolver;
        this.username = username;
        this.devLoginEnabled = devLoginEnabled;
        this.secureCookies = secureCookies;
        this.rememberMeEnabled = rememberMeEnabled;
    }

    public record LoginRequest(String username, String password) {}

    public record CsrfResponse(String token, String headerName) {}

    public record SessionResponse(String username) {}

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken csrfToken) {
        return new CsrfResponse(csrfToken.getToken(), csrfToken.getHeaderName());
    }

    @PostMapping("/login")
    public ResponseEntity<Void> login(
            @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        String remoteAddress = clientAddressResolver.resolve(servletRequest);
        if (!loginAttemptLimiter.tryAcquire(remoteAddress)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        if (request.username() == null || request.password() == null
                || request.username().length() > 128 || request.password().length() > 1024) {
            return ResponseEntity.badRequest().build();
        }

        final Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            request.username(), request.password()));
        } catch (AuthenticationException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        loginAttemptLimiter.reset(remoteAddress);
        establishSession(authentication, servletRequest, servletResponse);
        if (secureCookies && rememberMeEnabled) {
            rememberMeServices.loginSuccess(servletRequest, servletResponse, authentication);
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/dev-login")
    public ResponseEntity<Void> devLogin(
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        if (!devLoginEnabled
                || !"true".equals(servletRequest.getHeader("X-Cinelode-Dev-Login"))
                || !isLoopbackAddress(servletRequest.getRemoteAddr())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        UserDetails user = userDetailsService.loadUserByUsername(username);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                user, null, user.getAuthorities());
        establishSession(authentication, servletRequest, servletResponse);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/session")
    public SessionResponse session(Authentication authentication) {
        return new SessionResponse(authentication.getName());
    }

    private void establishSession(
            Authentication authentication,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        servletRequest.getSession(true);
        servletRequest.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, servletRequest, servletResponse);
    }

    private boolean isLoopbackAddress(String address) {
        try {
            return InetAddress.getByName(address).isLoopbackAddress();
        } catch (UnknownHostException exception) {
            return false;
        }
    }
}
