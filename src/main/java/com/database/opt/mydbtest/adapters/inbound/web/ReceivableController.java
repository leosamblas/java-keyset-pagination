package com.database.opt.mydbtest.adapters.inbound.web;

import com.database.opt.mydbtest.adapters.inbound.web.dto.CreateReceivableRequest;
import com.database.opt.mydbtest.adapters.inbound.web.dto.KeysetPageResponse;
import com.database.opt.mydbtest.adapters.inbound.web.dto.ReceivableResponse;
import com.database.opt.mydbtest.adapters.inbound.web.mapper.ReceivableWebMapper;
import com.database.opt.mydbtest.domain.ports.in.CreateReceivableBatchUseCase;
import com.database.opt.mydbtest.domain.ports.in.CreateReceivableCommand;
import com.database.opt.mydbtest.domain.ports.in.CreateReceivableUseCase;
import com.database.opt.mydbtest.domain.ports.in.FindReceivableByIdUseCase;
import com.database.opt.mydbtest.domain.ports.in.ListReceivablesKeysetUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/receivables")
public class ReceivableController {

    private final CreateReceivableUseCase createUseCase;
    private final CreateReceivableBatchUseCase createBatchUseCase;
    private final FindReceivableByIdUseCase findByIdUseCase;
    private final ListReceivablesKeysetUseCase listKeysetUseCase;

    public ReceivableController(
            CreateReceivableUseCase createUseCase,
            CreateReceivableBatchUseCase createBatchUseCase,
            FindReceivableByIdUseCase findByIdUseCase,
            ListReceivablesKeysetUseCase listKeysetUseCase
    ) {
        this.createUseCase = Objects.requireNonNull(createUseCase, "createUseCase must not be null");
        this.createBatchUseCase = Objects.requireNonNull(createBatchUseCase, "createBatchUseCase must not be null");
        this.findByIdUseCase = Objects.requireNonNull(findByIdUseCase, "findByIdUseCase must not be null");
        this.listKeysetUseCase = Objects.requireNonNull(listKeysetUseCase, "listKeysetUseCase must not be null");
    }

    @PostMapping
    public Mono<ResponseEntity<ReceivableResponse>> create(@Valid @RequestBody CreateReceivableRequest request) {
        CreateReceivableCommand command = ReceivableWebMapper.toCommand(request);
        return createUseCase.create(command)
                .map(ReceivableWebMapper::toResponse)
                .map(response -> ResponseEntity
                        .created(URI.create("/api/v1/receivables/" + response.id()))
                        .body(response));
    }

    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public Flux<ReceivableResponse> createBatch(@Valid @RequestBody List<CreateReceivableRequest> requests) {
        List<CreateReceivableCommand> commands = requests.stream()
                .map(ReceivableWebMapper::toCommand)
                .toList();
        return createBatchUseCase.createBatch(commands)
                .map(ReceivableWebMapper::toResponse);
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<ReceivableResponse>> findById(@PathVariable UUID id) {
        return findByIdUseCase.findById(id)
                .map(ReceivableWebMapper::toResponse)
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Receivable not found with id: " + id)));
    }

    @GetMapping
    public Mono<KeysetPageResponse<ReceivableResponse>> listKeyset(
            @RequestParam(required = false) String merchantId,
            @RequestParam(required = false) UUID cursor,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return listKeysetUseCase.listKeyset(merchantId, cursor, limit)
                .map(ReceivableWebMapper::toPageResponse);
    }
}

