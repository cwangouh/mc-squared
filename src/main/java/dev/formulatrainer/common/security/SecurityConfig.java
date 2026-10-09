package dev.formulatrainer.common.security;

import jakarta.servlet.http.HttpServletResponse;
import dev.formulatrainer.media.MediaProperties;
import java.io.IOException;
import java.time.Clock;
import java.util.Arrays;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({SecurityProperties.class, MediaProperties.class})
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http, SecurityErrorWriter securityErrorWriter, CsrfTokenRepository csrfTokenRepository,
        SecurityContextRepository securityContextRepository) throws Exception {
        http
            .authorizeHttpRequests(
                authorize -> authorize
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/auth/csrf",
                        "/api/v1/health",
                        "/api/v1/public/**",
                        "/api/v1/media/**",
                        "/media/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/login")
                    .permitAll()
                    .requestMatchers("/api/v1/test/**")
                    .permitAll()
                    .requestMatchers("/api/v1/auth/me", "/api/v1/auth/logout")
                    .authenticated()
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .denyAll())
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .securityContext(securityContext -> securityContext.securityContextRepository(securityContextRepository))
            .csrf(
                csrf -> csrf
                    .csrfTokenRepository(csrfTokenRepository)
                    .ignoringRequestMatchers("/api/v1/test/**"))
            .exceptionHandling(
                exceptions -> exceptions
                    .authenticationEntryPoint(
                        (request, response, exception) -> writeUnauthorized(securityErrorWriter, response))
                    .accessDeniedHandler(
                        (request, response, exception) -> writeForbidden(securityErrorWriter, response)));

        return http.build();
    }

    @Bean
    UserDetailsService userDetailsService(SecurityProperties securityProperties, Environment environment) {
        SecurityProperties.Admin admin = securityProperties.admin();
        if (isProd(environment) && isMissingAdminCredentials(admin)) {
            throw new IllegalStateException("ADMIN_USERNAME and ADMIN_PASSWORD_HASH must be set in prod profile");
        }
        return new InMemoryUserDetailsManager(
            User.withUsername(admin.username())
                .password(admin.passwordHash())
                .roles("ADMIN")
                .build());
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
        throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        HttpSessionCsrfTokenRepository repository = new HttpSessionCsrfTokenRepository();
        repository.setHeaderName("X-CSRF-TOKEN");
        return repository;
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    private static boolean isProd(Environment environment) {
        return Arrays.asList(environment.getActiveProfiles())
            .contains("prod");
    }

    private static boolean isMissingAdminCredentials(SecurityProperties.Admin admin) {
        return admin == null || isBlank(admin.username()) || isBlank(admin.passwordHash());
    }

    private static void writeUnauthorized(SecurityErrorWriter securityErrorWriter, HttpServletResponse response)
        throws IOException {
        securityErrorWriter.write(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required");
    }

    private static void writeForbidden(SecurityErrorWriter securityErrorWriter, HttpServletResponse response)
        throws IOException {
        securityErrorWriter.write(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Access is denied");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
