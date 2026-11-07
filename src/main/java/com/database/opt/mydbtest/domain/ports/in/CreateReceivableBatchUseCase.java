package com.database.opt.mydbtest.domain.ports.in;

import com.database.opt.mydbtest.domain.model.Receivable;
import reactor.core.publisher.Flux;

import java.util.List;

public interface CreateReceivableBatchUseCase {
    Flux<Receivable> createBatch(List<CreateReceivableCommand> commands);
}

