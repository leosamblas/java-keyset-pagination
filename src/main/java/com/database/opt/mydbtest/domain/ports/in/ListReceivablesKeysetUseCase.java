package com.database.opt.mydbtest.domain.ports.in;

import com.database.opt.mydbtest.domain.model.KeysetPage;
import com.database.opt.mydbtest.domain.model.Receivable;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ListReceivablesKeysetUseCase {
    Mono<KeysetPage<Receivable>> listKeyset(String merchantId, UUID cursor, int limit);
}
