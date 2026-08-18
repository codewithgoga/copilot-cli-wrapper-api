package com.gd.copilotapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI copilotCliWrapperOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Copilot CLI Wrapper API")
                        .description("OpenAI-compatible HTTP wrapper around the GitHub Copilot CLI.")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList("githubPat"))
                .components(new Components()
                        .addSecuritySchemes("githubPat", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("GitHub PAT")
                                .description("GitHub personal access token forwarded to gh via GH_TOKEN/GITHUB_TOKEN.")));
    }
}
