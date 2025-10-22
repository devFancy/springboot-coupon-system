package dev.be.coupon.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    public static final String AUTHORIZATION = "Authorization";

    @Bean
    public OpenAPI openAPI() {
        Components components = new Components()
                .addSecuritySchemes(
                        AUTHORIZATION,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .in(SecurityScheme.In.HEADER)
                                .name(AUTHORIZATION)
                                .description("(Bearer) ${ACCESS_TOKEN}")
                );
        SecurityRequirement securityRequirement = new SecurityRequirement().addList(AUTHORIZATION);


        return new OpenAPI()
                .components(components)
                .addSecurityItem(securityRequirement)
                .addServersItem(new Server().url("http://localhost:8080"))
                .info(getServerInfo());
    }

    private Info getServerInfo() {
        return new Info()
                .title("[Coupon] Server API")
                .description("[Coupon] Server API 명세서입니다.")
                .version("1.4.3");
    }
}
