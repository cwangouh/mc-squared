package dev.formulatrainer.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.security")
public record SecurityProperties(Admin admin) {

    public record Admin(String username, String passwordHash) {
    }
}
