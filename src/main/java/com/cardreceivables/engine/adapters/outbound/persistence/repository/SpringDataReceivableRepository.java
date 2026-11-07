package com.cardreceivables.engine.adapters.outbound.persistence.repository;

import com.cardreceivables.engine.adapters.outbound.persistence.entity.ReceivableEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface SpringDataReceivableRepository extends ReactiveCrudRepository<ReceivableEntity, UUID> {

    @Query("SELECT * FROM receivables WHERE merchant_id = :merchantId ORDER BY id DESC LIMIT :limit")
    Flux<ReceivableEntity> findFirstPageByMerchant(String merchantId, int limit);

    @Query("SELECT * FROM receivables WHERE merchant_id = :merchantId AND id < :cursor ORDER BY id DESC LIMIT :limit")
    Flux<ReceivableEntity> findNextPageByMerchant(String merchantId, UUID cursor, int limit);

    @Query("SELECT * FROM receivables ORDER BY id DESC LIMIT :limit")
    Flux<ReceivableEntity> findFirstPageGlobal(int limit);

    @Query("SELECT * FROM receivables WHERE id < :cursor ORDER BY id DESC LIMIT :limit")
    Flux<ReceivableEntity> findNextPageGlobal(UUID cursor, int limit);
}

