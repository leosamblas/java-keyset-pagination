package com.database.opt.mydbtest.adapters.outbound.persistence.adapter;

import com.database.opt.mydbtest.adapters.outbound.persistence.entity.ReceivableEntity;
import com.database.opt.mydbtest.adapters.outbound.persistence.mapper.ReceivablePersistenceMapper;
import com.database.opt.mydbtest.adapters.outbound.persistence.repository.SpringDataReceivableRepository;
import com.database.opt.mydbtest.domain.model.Receivable;
import com.database.opt.mydbtest.domain.ports.out.ReceivableRepositoryPort;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class ReceivablePersistenceAdapter implements ReceivableRepositoryPort {

    private final SpringDataReceivableRepository repository;

    public ReceivablePersistenceAdapter(SpringDataReceivableRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public Mono<Receivable> save(Receivable receivable) {
        ReceivableEntity entity = ReceivablePersistenceMapper.toEntity(receivable);
        return repository.save(entity)
                .map(ReceivablePersistenceMapper::toDomain);
    }

    @Override
    public Flux<Receivable> saveAll(List<Receivable> receivables) {
        List<ReceivableEntity> entities = receivables.stream()
                .map(ReceivablePersistenceMapper::toEntity)
                .toList();
        return repository.saveAll(entities)
                .map(ReceivablePersistenceMapper::toDomain);
    }

    @Override
    public Mono<Receivable> findById(UUID id) {
        return repository.findById(id)
                .map(ReceivablePersistenceMapper::toDomain);
    }

    @Override
    public Flux<Receivable> findKeyset(String merchantId, UUID cursor, int fetchLimit) {
        Flux<ReceivableEntity> entities;
        boolean hasMerchant = (merchantId != null && !merchantId.isBlank());

        if (hasMerchant) {
            if (cursor == null) {
                entities = repository.findFirstPageByMerchant(merchantId, fetchLimit);
            } else {
                entities = repository.findNextPageByMerchant(merchantId, cursor, fetchLimit);
            }
        } else {
            if (cursor == null) {
                entities = repository.findFirstPageGlobal(fetchLimit);
            } else {
                entities = repository.findNextPageGlobal(cursor, fetchLimit);
            }
        }

        return entities.map(ReceivablePersistenceMapper::toDomain);
    }
}

