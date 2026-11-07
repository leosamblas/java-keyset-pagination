package com.database.opt.mydbtest.infrastructure.config;

import com.database.opt.mydbtest.application.service.ReceivableApplicationService;
import com.database.opt.mydbtest.domain.ports.out.ReceivableRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfiguration {

    @Bean
    public ReceivableApplicationService receivableApplicationService(ReceivableRepositoryPort repositoryPort) {
        return new ReceivableApplicationService(repositoryPort);
    }
}
