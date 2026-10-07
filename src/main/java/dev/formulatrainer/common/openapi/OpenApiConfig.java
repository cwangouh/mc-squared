package dev.formulatrainer.common.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI formulaTrainerOpenApi() {
        return new OpenAPI().info(new Info().title("Formula Trainer API")
                .version("0.0.1")
                .description("Backend API for formula training decks and cards."));
    }

}
