package com.database.opt.mydbtest.application.service;

import com.database.opt.mydbtest.domain.model.KeysetPage;
import com.database.opt.mydbtest.domain.model.Receivable;
import com.database.opt.mydbtest.domain.ports.in.CreateReceivableBatchUseCase;
import com.database.opt.mydbtest.domain.ports.in.CreateReceivableCommand;
import com.database.opt.mydbtest.domain.ports.in.CreateReceivableUseCase;
import com.database.opt.mydbtest.domain.ports.in.FindReceivableByIdUseCase;
import com.database.opt.mydbtest.domain.ports.in.ListReceivablesKeysetUseCase;
import com.database.opt.mydbtest.domain.ports.out.ReceivableRepositoryPort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class ReceivableApplicationService implements
        CreateReceivableUseCase,
        CreateReceivableBatchUseCase,
        FindReceivableByIdUseCase,
        ListReceivablesKeysetUseCase {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final ReceivableRepositoryPort repositoryPort;

    public ReceivableApplicationService(ReceivableRepositoryPort repositoryPort) {
        this.repositoryPort = Objects.requireNonNull(repositoryPort, "repositoryPort must not be null");
    }

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

