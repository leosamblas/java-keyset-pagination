package com.cardreceivables.engine.adapters.inbound.web;

import com.cardreceivables.engine.adapters.inbound.web.dto.CreateReceivableRequest;
import com.cardreceivables.engine.domain.model.CardBrand;
import com.cardreceivables.engine.domain.model.KeysetPage;
import com.cardreceivables.engine.domain.model.Receivable;
import com.cardreceivables.engine.domain.ports.in.CreateReceivableBatchUseCase;
import com.cardreceivables.engine.domain.ports.in.CreateReceivableCommand;
import com.cardreceivables.engine.domain.ports.in.CreateReceivableUseCase;
import com.cardreceivables.engine.domain.ports.in.FindReceivableByIdUseCase;
import com.cardreceivables.engine.domain.ports.in.ListReceivablesKeysetUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = ReceivableController.class)
@Import(GlobalExceptionHandler.class)
class ReceivableControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private CreateReceivableUseCase createUseCase;

    @MockitoBean
    private CreateReceivableBatchUseCase createBatchUseCase;

    @MockitoBean
    private FindReceivableByIdUseCase findByIdUseCase;

    @MockitoBean
    private ListReceivablesKeysetUseCase listKeysetUseCase;

    @Test
    @DisplayName("POST /api/v1/receivables valido deve retornar 201 Created com header Location")
    void shouldCreateReceivableSuccessfully() {
        UUID generatedId = UUID.randomUUID();
        Receivable mockReceivable = Receivable.builder()
                .id(generatedId)
                .merchantId("merch-abc")
                .cardBrand(CardBrand.VISA)
                .grossAmount(new BigDecimal("150.00"))
                .feeRate(new BigDecimal("0.0200"))
                .transactionDate(Instant.now())
                .build();

        when(createUseCase.create(any(CreateReceivableCommand.class)))
                .thenReturn(Mono.just(mockReceivable));

        CreateReceivableRequest request = new CreateReceivableRequest(
                "merch-abc",
                CardBrand.VISA,
                new BigDecimal("150.00"),
                new BigDecimal("0.0200"),
                1,
                1,
                Instant.now()
        );

        webTestClient.post()
                .uri("/api/v1/receivables")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueEquals("Location", "/api/v1/receivables/" + generatedId)
                .expectBody()
                .jsonPath("$.id").isEqualTo(generatedId.toString())
                .jsonPath("$.merchantId").isEqualTo("merch-abc")
                .jsonPath("$.cardBrand").isEqualTo("VISA")
                .jsonPath("$.grossAmount").isEqualTo(150.00)
                .jsonPath("$.feeAmount").isEqualTo(3.00)
                .jsonPath("$.netAmount").isEqualTo(147.00);
    }

    @Test
    @DisplayName("POST /api/v1/receivables com grossAmount negativo deve retornar 400 Bad Request estruturado")
    void shouldReturnBadRequestWhenGrossAmountIsNegative() {
        CreateReceivableRequest invalidRequest = new CreateReceivableRequest(
                "merch-abc",
                CardBrand.VISA,
                new BigDecimal("-50.00"),
                new BigDecimal("0.02"),
                1,
                1,
                Instant.now()
        );

        webTestClient.post()
                .uri("/api/v1/receivables")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidRequest)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.error").isEqualTo("Bad Request")
                .jsonPath("$.message").isEqualTo("Validation failed for request")
                .jsonPath("$.details[0]").value(val -> assertThat((String) val).contains("grossAmount"));
    }

    @Test
    @DisplayName("GET /api/v1/receivables/{id} existente deve retornar 200 OK")
    void shouldReturnReceivableById() {
        UUID id = UUID.randomUUID();
        Receivable mockReceivable = Receivable.builder()
                .id(id)
                .merchantId("merch-abc")
                .cardBrand(CardBrand.MASTERCARD)
                .grossAmount(new BigDecimal("200.00"))
                .feeRate(new BigDecimal("0.02"))
                .build();

        when(findByIdUseCase.findById(id)).thenReturn(Mono.just(mockReceivable));

        webTestClient.get()
                .uri("/api/v1/receivables/{id}", id)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(id.toString())
                .jsonPath("$.merchantId").isEqualTo("merch-abc");
    }

    @Test
    @DisplayName("GET /api/v1/receivables/{id} inexistente deve retornar 404 Not Found")
    void shouldReturnNotFoundWhenReceivableDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(findByIdUseCase.findById(id)).thenReturn(Mono.empty());

        webTestClient.get()
                .uri("/api/v1/receivables/{id}", id)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.status").isEqualTo(404);
    }

    @Test
    @DisplayName("Erro 500 inesperado nao deve vazar mensagem interna no corpo da resposta")
    void shouldSanitizeGeneralExceptionMessage() {
        when(findByIdUseCase.findById(any())).thenThrow(new RuntimeException("SQL syntax error near secret_table; credentials leaked"));

        webTestClient.get()
                .uri("/api/v1/receivables/{id}", UUID.randomUUID())
                .exchange()
                .expectStatus().is5xxServerError()
                .expectBody()
                .jsonPath("$.status").isEqualTo(500)
                .jsonPath("$.message").isEqualTo("An unexpected internal error occurred");
    }

    @Test
    @DisplayName("GET /api/v1/receivables com Keyset Pagination deve retornar estrutura de página com cursor")
    void shouldReturnKeysetPageResponse() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        Receivable r1 = Receivable.builder().id(id1).merchantId("m1").cardBrand(CardBrand.VISA).grossAmount(new BigDecimal("100.00")).feeRate(new BigDecimal("0.02")).build();
        Receivable r2 = Receivable.builder().id(id2).merchantId("m1").cardBrand(CardBrand.VISA).grossAmount(new BigDecimal("200.00")).feeRate(new BigDecimal("0.02")).build();

        KeysetPage<Receivable> page = new KeysetPage<>(List.of(r1, r2), 2, id2, true);

        when(listKeysetUseCase.listKeyset(eq("m1"), eq(null), eq(2)))
                .thenReturn(Mono.just(page));

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/receivables")
                        .queryParam("merchantId", "m1")
                        .queryParam("limit", 2)
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(2)
                .jsonPath("$.limit").isEqualTo(2)
                .jsonPath("$.nextCursor").isEqualTo(id2.toString())
                .jsonPath("$.hasNext").isEqualTo(true);
    }
}
