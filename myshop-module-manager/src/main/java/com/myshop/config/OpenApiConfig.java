package com.myshop.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI myShopOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("MyShop Manager API")
                        .description("API documentation for JAVA-ecommerce-backend-api-MEMBER")
                        .version("v1.0.0")
                        .contact(new Contact().name("MyShop Team"))
                        .license(new License().name("Apache 2.0")));
    }
}
