package com.cardreceivables.engine.adapters.inbound.web;

import com.cardreceivables.engine.adapters.inbound.web.dto.CreateReceivableRequest;
import com.cardreceivables.engine.adapters.inbound.web.dto.ErrorResponse;
import com.cardreceivables.engine.adapters.inbound.web.dto.KeysetPageResponse;
import com.cardreceivables.engine.adapters.inbound.web.dto.ReceivableResponse;
import com.cardreceivables.engine.adapters.inbound.web.mapper.ReceivableWebMapper;
import com.cardreceivables.engine.domain.ports.in.CreateReceivableBatchUseCase;
import com.cardreceivables.engine.domain.ports.in.CreateReceivableCommand;
import com.cardreceivables.engine.domain.ports.in.CreateReceivableUseCase;
import com.cardreceivables.engine.domain.ports.in.FindReceivableByIdUseCase;
import com.cardreceivables.engine.domain.ports.in.ListReceivablesKeysetUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Recebíveis", description = "Operações reativas de cadastro e consulta de recebíveis de cartão com Keyset Pagination e PostgreSQL 18 UUIDv7")
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
    @Operation(
            summary = "Cadastra um recebível individual",
            description = "Calcula automaticamente a taxa MDR (arredondamento HALF_UP na escala 2) e o valor líquido. " +
                    "O ID do recebível é gerado nativamente pelo PostgreSQL 18 utilizando a função `uuidv7()` (RFC 9562)."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Recebível cadastrado com sucesso",
                    headers = @Header(name = "Location", description = "URI do recurso criado", schema = @Schema(type = "string")),
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ReceivableResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Requisição inválida (falha de validação nos campos obrigatórios ou valores negativos)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public Mono<ResponseEntity<ReceivableResponse>> create(@Valid @RequestBody CreateReceivableRequest request) {
        CreateReceivableCommand command = ReceivableWebMapper.toCommand(request);
        return createUseCase.create(command)
                .map(ReceivableWebMapper::toResponse)
                .map(response -> ResponseEntity
                        .created(URI.create("/api/v1/receivables/" + response.id()))
                        .body(response));
    }

    @PostMapping("/batch")
    @Operation(
            summary = "Cadastra recebíveis em lote (Batch)",
            description = "Processa uma coleção de recebíveis de forma reativa e não-bloqueante, retornando a lista de recebíveis criados."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Lote de recebíveis cadastrado com sucesso",
                    content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = ReceivableResponse.class)))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Dados inválidos no lote",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public Mono<ResponseEntity<List<ReceivableResponse>>> createBatch(@RequestBody List<@Valid CreateReceivableRequest> requests) {
        List<CreateReceivableCommand> commands = requests.stream()
                .map(ReceivableWebMapper::toCommand)
                .toList();
        return createBatchUseCase.createBatch(commands)
                .map(ReceivableWebMapper::toResponse)
                .collectList()
                .map(responses -> ResponseEntity.status(HttpStatus.CREATED).body(responses));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Busca recebível por ID",
            description = "Recupera os detalhes completos de um recebível a partir do seu identificador único UUIDv7."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Recebível encontrado com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ReceivableResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Recebível não encontrado para o ID informado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public Mono<ResponseEntity<ReceivableResponse>> findById(
            @Parameter(description = "Identificador único do recebível (UUIDv7)", example = "01930777-a123-7456-8abc-def012345678", required = true)
            @PathVariable UUID id
    ) {
        return findByIdUseCase.findById(id)
                .map(ReceivableWebMapper::toResponse)
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Receivable not found with id: " + id)));
    }

    @GetMapping
    @Operation(
            summary = "Consulta paginada via Keyset (Seek) Pagination",
            description = "Paginação de alta performance sem OFFSET utilizando o UUIDv7 como cursor temporal monotônico indexado. " +
                    "Executa a busca diretamente no índice B-tree em complexidade O(limit). " +
                    "Para a primeira página, omita o parâmetro `cursor`. Para páginas subsequentes, forneça o `nextCursor` retornado na resposta anterior."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Página de recebíveis recuperada com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = KeysetPageResponse.class))
            )
    })
    public Mono<KeysetPageResponse<ReceivableResponse>> listKeyset(
            @Parameter(description = "Identificador opcional do lojista para filtrar os recebíveis", example = "merch-1001")
            @RequestParam(required = false) String merchantId,

            @Parameter(description = "Cursor temporal monotônico (UUIDv7 do último item da página anterior). Omitir na primeira página.", example = "01930777-a120-7456-8abc-def012345678")
            @RequestParam(required = false) UUID cursor,

            @Parameter(description = "Quantidade de registros por página (padrão: 20, máximo permitido: 100)", example = "20")
            @RequestParam(defaultValue = "20") int limit
    ) {
        return listKeysetUseCase.listKeyset(merchantId, cursor, limit)
                .map(ReceivableWebMapper::toPageResponse);
    }
}
