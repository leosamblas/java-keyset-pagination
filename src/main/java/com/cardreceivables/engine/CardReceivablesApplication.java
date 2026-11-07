package com.cardreceivables.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@Slf4j
@SpringBootApplication
public class CardReceivablesApplication {

    public static void main(String[] args) {
        SpringApplication.run(CardReceivablesApplication.class, args);
    }

    @Bean
    public CommandLineRunner startupSummary(Environment env) {
        return args -> {
            String port = env.getProperty("server.port", "8080");
            String appName = env.getProperty("spring.application.name", "card-receivables-engine");
            log.info("====================================================================");
            log.info("  🚀 {} inicializado com sucesso!", appName);
            log.info("  📡 Base URL:    http://localhost:{}", port);
            log.info("  📚 Swagger UI:  http://localhost:{}/swagger-ui.html", port);
            log.info("  📄 OpenAPI Doc: http://localhost:{}/v3/api-docs", port);
            log.info("  ⚡ Engine:      Spring WebFlux + R2DBC + PostgreSQL 18 UUIDv7");
            log.info("====================================================================");
        };
    }
}

