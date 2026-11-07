package com.cardreceivables.engine.domain.ports.in;

import com.cardreceivables.engine.domain.model.Receivable;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface FindReceivableByIdUseCase {
    Mono<Receivable> findById(UUID id);
}

