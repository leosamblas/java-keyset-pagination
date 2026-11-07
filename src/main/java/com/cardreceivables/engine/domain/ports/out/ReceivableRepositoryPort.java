package com.cardreceivables.engine.domain.ports.out;

import com.cardreceivables.engine.domain.model.Receivable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface ReceivableRepositoryPort {
    Mono<Receivable> save(Receivable receivable);
    Flux<Receivable> saveAll(List<Receivable> receivables);
    Mono<Receivable> findById(UUID id);
    Flux<Receivable> findKeyset(String merchantId, UUID cursor, int fetchLimit);
}

