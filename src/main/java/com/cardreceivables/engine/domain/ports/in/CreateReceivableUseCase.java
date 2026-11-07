package com.cardreceivables.engine.domain.ports.in;

import com.cardreceivables.engine.domain.model.Receivable;
import reactor.core.publisher.Mono;

public interface CreateReceivableUseCase {
    Mono<Receivable> create(CreateReceivableCommand command);
}

