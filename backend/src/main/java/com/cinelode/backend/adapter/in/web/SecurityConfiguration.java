package com.cinelode.backend.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/** Secures all application endpoints and uses browser sessions for media requests. */
@Configuration
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository,
            CookieCsrfTokenRepository csrfTokenRepository,
            TokenBasedRememberMeServices rememberMeServices,
            @Value("${server.servlet.session.cookie.secure:true}") boolean secureCookies,
            @Value("${cinelode.security.remember-me-enabled:false}") boolean rememberMeEnabled) throws Exception {
        http
                .cors(cors -> cors.disable())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .securityContext(context -> context
                        .securityContextRepository(securityContextRepository))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId()))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/auth/csrf",
                                "/api/auth/login",
                                "/api/auth/dev-login").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                response.sendError(401)))
                .requestCache(cache -> cache.disable())
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(204)));
        if (secureCookies && rememberMeEnabled) {
            http.rememberMe(remember -> remember
                    .key(rememberMeServices.getKey())
                    .rememberMeServices(rememberMeServices));
        }
        return http.build();
    }

    @Bean
    TokenBasedRememberMeServices rememberMeServices(
            UserDetailsService userDetailsService,
            @Value("${cinelode.security.password-hash:}") String passwordHash) {
        TokenBasedRememberMeServices services =
                new TokenBasedRememberMeServices(passwordHash, userDetailsService) {
                    @Override
                    public Authentication autoLogin(HttpServletRequest request, HttpServletResponse response) {
                        Authentication authentication = super.autoLogin(request, response);
                        if (authentication != null) {
                            loginSuccess(request, response, authentication);
                        }
                        return authentication;
                    }
                };
        services.setAlwaysRemember(true);
        services.setUseSecureCookie(true);
        services.setTokenValiditySeconds(10 * 365 * 24 * 60 * 60);
        services.setCookieCustomizer(cookie -> cookie.setAttribute("SameSite", "Strict"));
        return services;
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CookieCsrfTokenRepository csrfTokenRepository(
            @Value("${server.servlet.session.cookie.secure:true}") boolean secureCookies) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie
                .secure(secureCookies)
                .sameSite("Strict")
                .path("/"));
        return repository;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(
            @Value("${cinelode.security.username:cinelode}") String username,
            @Value("${cinelode.security.password-hash:}") String passwordHash) {
        if (passwordHash.isBlank()) {
            throw new IllegalStateException(
                    "CINELODE_PASSWORD_HASH is missing. Set it to a Spring Security encoded password "
                            + "in backend/.env or the process environment before starting Cinelode. "
                            + "See the password setup instructions in README.md.");
        }
        return new InMemoryUserDetailsManager(
                User.withUsername(username)
                        .password(passwordHash)
                        .roles("USER")
                        .build());
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
