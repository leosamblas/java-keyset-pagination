package com.cardreceivables.engine.domain.ports.in;

import com.cardreceivables.engine.domain.model.KeysetPage;
import com.cardreceivables.engine.domain.model.Receivable;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ListReceivablesKeysetUseCase {
    Mono<KeysetPage<Receivable>> listKeyset(String merchantId, UUID cursor, int limit);
}

