package com.spry.library.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI libraryServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Library Service API")
                        .description("API for managing books, wishlists, and user notifications in the library system")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Library Service Team")
                                .email("library@company.com")))
                .servers(List.of(
                        new Server().url("http://localhost:8081").description("Development server")
                ));
    }
}