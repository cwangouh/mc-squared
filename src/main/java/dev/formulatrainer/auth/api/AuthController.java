package dev.formulatrainer.auth.api;

import dev.formulatrainer.common.api.ApiErrorResponse;
import dev.formulatrainer.common.security.LoginAttemptLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;

    private final SecurityContextRepository securityContextRepository;

    private final CsrfTokenRepository csrfTokenRepository;

    private final LoginAttemptLimiter loginAttemptLimiter;

    public AuthController(
        AuthenticationManager authenticationManager, SecurityContextRepository securityContextRepository,
        CsrfTokenRepository csrfTokenRepository, LoginAttemptLimiter loginAttemptLimiter) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
        this.loginAttemptLimiter = loginAttemptLimiter;
    }

    @GetMapping("/csrf")
    public ResponseEntity<CsrfResponse> csrf(CsrfToken csrfToken) {
        CsrfResponse response = new CsrfResponse(
            csrfToken.getToken(), csrfToken.getHeaderName(), csrfToken.getParameterName());
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<ApiErrorResponse> login(
        @Valid
        @RequestBody
        LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String clientAddress = httpRequest.getRemoteAddr();
        if (loginAttemptLimiter.isLimited(clientAddress)) {
            return error(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_RATE_LIMITED", "Too many failed login attempts");
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
            loginAttemptLimiter.reset(clientAddress);

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

            rotateSessionId(httpRequest);
            securityContextRepository.saveContext(context, httpRequest, httpResponse);
            csrfTokenRepository.saveToken(null, httpRequest, httpResponse);

            return ResponseEntity.noContent()
                .build();
        } catch (BadCredentialsException exception) {
            loginAttemptLimiter.recordFailure(clientAddress);
            return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid username or password");
        }
    }

    @GetMapping("/me")
    public MeResponse me(Principal principal) {
        return new MeResponse(principal.getName());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        SecurityContextHolder.clearContext();
        securityContextRepository.saveContext(SecurityContextHolder.createEmptyContext(), request, response);
        csrfTokenRepository.saveToken(null, request, response);
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent()
            .build();
    }

    private static ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
            .body(new ApiErrorResponse(status.value(), code, message));
    }

    private static void rotateSessionId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            request.changeSessionId();
        }
    }

}
