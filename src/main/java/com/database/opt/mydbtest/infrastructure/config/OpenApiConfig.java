package com.database.opt.mydbtest.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("API de Gestão de Recebíveis de Cartão")
                        .version("1.0.0")
                        .description("API reativa de alto desempenho para cadastro e consulta de recebíveis de cartão de crédito e débito. " +
                                "Utiliza ordenação temporal nativa via PostgreSQL 18 UUIDv7 (RFC 9562), Spring WebFlux, " +
                                "Spring Data R2DBC e Keyset Pagination (Seek Pagination) sem OFFSET.")
                        .contact(new Contact()
                                .name("Equipe de Engenharia")
                                .email("engineering@database.opt.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}
