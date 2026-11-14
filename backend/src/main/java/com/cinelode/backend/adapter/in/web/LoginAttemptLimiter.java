package com.cinelode.backend.adapter.in.web;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Applies a bounded per-source-address login attempt limit. */
@Component
public class LoginAttemptLimiter {
    private static final int MAX_TRACKED_ADDRESSES = 512;
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final Clock clock;
    private final Map<String, AttemptWindow> attempts = new LinkedHashMap<>();

    public LoginAttemptLimiter() {
        this(Clock.systemUTC());
    }

    LoginAttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    public synchronized boolean tryAcquire(String address) {
        Instant now = clock.instant();
        removeExpired(now);
        AttemptWindow window = attempts.get(address);
        if (window == null) {
            if (attempts.size() >= MAX_TRACKED_ADDRESSES) {
                return false;
            }
            window = new AttemptWindow(now.plus(WINDOW));
            attempts.put(address, window);
        }
        if (window.attempts >= MAX_ATTEMPTS) {
            return false;
        }
        window.attempts++;
        return true;
    }

    public synchronized void reset(String address) {
        attempts.remove(address);
    }

    private void removeExpired(Instant now) {
        Iterator<AttemptWindow> windows = attempts.values().iterator();
        while (windows.hasNext()) {
            if (!now.isBefore(windows.next().expiresAt)) {
                windows.remove();
            }
        }
    }

    private static final class AttemptWindow {
        private final Instant expiresAt;
        private int attempts;

        private AttemptWindow(Instant expiresAt) {
            this.expiresAt = expiresAt;
        }
    }
}
