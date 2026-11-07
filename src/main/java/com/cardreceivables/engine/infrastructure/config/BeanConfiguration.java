package com.cardreceivables.engine.infrastructure.config;

import com.cardreceivables.engine.application.service.ReceivableApplicationService;
import com.cardreceivables.engine.domain.ports.out.ReceivableRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfiguration {

    @Bean
    public ReceivableApplicationService receivableApplicationService(ReceivableRepositoryPort repositoryPort) {
        return new ReceivableApplicationService(repositoryPort);
    }
}

