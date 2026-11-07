package com.database.opt.mydbtest.domain.ports.in;

import com.database.opt.mydbtest.domain.model.Receivable;
import reactor.core.publisher.Mono;

public interface CreateReceivableUseCase {
    Mono<Receivable> create(CreateReceivableCommand command);
}

