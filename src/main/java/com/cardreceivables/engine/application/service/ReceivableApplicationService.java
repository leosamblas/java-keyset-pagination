package com.cardreceivables.engine.application.service;

import com.cardreceivables.engine.domain.model.KeysetPage;
import com.cardreceivables.engine.domain.model.Receivable;
import com.cardreceivables.engine.domain.ports.in.CreateReceivableBatchUseCase;
import com.cardreceivables.engine.domain.ports.in.CreateReceivableCommand;
import com.cardreceivables.engine.domain.ports.in.CreateReceivableUseCase;
import com.cardreceivables.engine.domain.ports.in.FindReceivableByIdUseCase;
import com.cardreceivables.engine.domain.ports.in.ListReceivablesKeysetUseCase;
import com.cardreceivables.engine.domain.ports.out.ReceivableRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class ReceivableApplicationService implements
        CreateReceivableUseCase,
        CreateReceivableBatchUseCase,
        FindReceivableByIdUseCase,
        ListReceivablesKeysetUseCase {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final ReceivableRepositoryPort repositoryPort;

    @Override
    public Mono<Receivable> create(CreateReceivableCommand command) {
        Receivable receivable = toDomain(command);
        return repositoryPort.save(receivable);
    }

    @Override
    public Flux<Receivable> createBatch(List<CreateReceivableCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return Flux.empty();
        }
        List<Receivable> receivables = commands.stream()
                .map(this::toDomain)
                .toList();
        return repositoryPort.saveAll(receivables);
    }

    @Override
    public Mono<Receivable> findById(UUID id) {
        return repositoryPort.findById(id);
    }

    @Override
    public Mono<KeysetPage<Receivable>> listKeyset(String merchantId, UUID cursor, int limit) {
        int sanitizedLimit = (limit <= 0) ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        int fetchLimit = sanitizedLimit + 1;
        return repositoryPort.findKeyset(merchantId, cursor, fetchLimit)
                .collectList()
                .map(items -> KeysetPage.of(items, sanitizedLimit, Receivable::getId));
    }

    private Receivable toDomain(CreateReceivableCommand cmd) {
        return Receivable.builder()
                .merchantId(cmd.merchantId())
                .cardBrand(cmd.cardBrand())
                .grossAmount(cmd.grossAmount())
                .feeRate(cmd.feeRate())
                .installments(cmd.installments())
                .installmentNumber(cmd.installmentNumber())
                .transactionDate(cmd.transactionDate())
                .build();
    }
}

