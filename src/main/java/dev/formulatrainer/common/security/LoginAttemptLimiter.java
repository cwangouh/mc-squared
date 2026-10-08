package dev.formulatrainer.common.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class LoginAttemptLimiter {

    private static final int MAX_FAILURES = 5;

    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final Clock clock;

    private final Map<String, AttemptWindow> attempts = new ConcurrentHashMap<>();

    public LoginAttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    public boolean isLimited(String clientAddress) {
        AttemptWindow window = attempts.get(clientAddress);
        if (window == null || window.isExpired(now())) {
            return false;
        }
        return window.failures >= MAX_FAILURES;
    }

    public void recordFailure(String clientAddress) {
        Instant now = now();
        attempts.compute(clientAddress, (key, window) -> {
            if (window == null || window.isExpired(now)) {
                return new AttemptWindow(now.plus(WINDOW), 1);
            }
            return new AttemptWindow(window.expiresAt, window.failures + 1);
        });
    }

    public void reset(String clientAddress) {
        attempts.remove(clientAddress);
    }

    public void clear() {
        attempts.clear();
    }

    private Instant now() {
        return clock.instant();
    }

    private record AttemptWindow(Instant expiresAt, int failures) {

        boolean isExpired(Instant now) {
            return !now.isBefore(expiresAt);
        }
    }

}
