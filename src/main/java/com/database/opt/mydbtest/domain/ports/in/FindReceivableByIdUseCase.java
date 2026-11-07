package com.database.opt.mydbtest.domain.ports.in;

import com.database.opt.mydbtest.domain.model.Receivable;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface FindReceivableByIdUseCase {
    Mono<Receivable> findById(UUID id);
}

