package com.cardreceivables.engine.domain.ports.in;

import com.cardreceivables.engine.domain.model.Receivable;
import reactor.core.publisher.Flux;

import java.util.List;

public interface CreateReceivableBatchUseCase {
    Flux<Receivable> createBatch(List<CreateReceivableCommand> commands);
}

