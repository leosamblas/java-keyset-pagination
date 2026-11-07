package com.cardreceivables.engine;

import com.cardreceivables.engine.adapters.inbound.web.dto.CreateReceivableRequest;
import com.cardreceivables.engine.adapters.inbound.web.dto.KeysetPageResponse;
import com.cardreceivables.engine.adapters.inbound.web.dto.ReceivableResponse;
import com.cardreceivables.engine.domain.model.CardBrand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class UuidV7KeysetPaginationIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    private static final ParameterizedTypeReference<KeysetPageResponse<ReceivableResponse>> PAGE_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    @Test
    @DisplayName("Deveria comprovar ordenação temporal nativa de UUIDv7 e Keyset Pagination contínua de ponta a ponta")
    void shouldProveUuidV7TemporalOrderingAndKeysetPaginationEndToEnd() throws InterruptedException {
        String merchantId = "merchant-e2e-" + UUID.randomUUID();
        int totalReceivables = 15;
        int pageSize = 5;

        // 1. Cadastra 15 recebíveis de forma sequencial com pequenos atrasos para garantir timestamps monotônicos crescentes no UUIDv7
        List<UUID> createdIds = new ArrayList<>();
        for (int i = 1; i <= totalReceivables; i++) {
            CreateReceivableRequest req = new CreateReceivableRequest(
                    merchantId,
                    (i % 2 == 0) ? CardBrand.VISA : CardBrand.MASTERCARD,
                    new BigDecimal("100.00").add(BigDecimal.valueOf(i)),
                    new BigDecimal("0.0200"),
                    1,
                    1,
                    Instant.now()
            );

            ReceivableResponse created = webTestClient.post()
                    .uri("/api/v1/receivables")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(req)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody(ReceivableResponse.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(created).isNotNull();
            assertThat(created.id()).isNotNull();
            // Comprovação estrita da RFC 9562: UUID Versão 7
            assertThat(created.id().version()).isEqualTo(7);
            createdIds.add(created.id());

            // Atraso de 15ms entre inserções para garantir avanço claro do timestamp de 48 bits do UUIDv7
            Thread.sleep(15);
        }

        assertThat(createdIds).hasSize(totalReceivables);

        // Como foram criados sequencialmente no tempo, os UUIDs devem ser monotônicos crescentes na inserção:
        for (int i = 0; i < createdIds.size() - 1; i++) {
            assertThat(createdIds.get(i).compareTo(createdIds.get(i + 1)))
                    .as("UUIDv7 monotônico crescente na inserção no índice %d", i)
                    .isLessThan(0);
        }

        Set<UUID> allSeenIds = new HashSet<>();

        // 2. Requisita Página 1: limit 5 (sem cursor)
        KeysetPageResponse<ReceivableResponse> page1 = webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/receivables")
                        .queryParam("merchantId", merchantId)
                        .queryParam("limit", pageSize)
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(PAGE_RESPONSE_TYPE)
                .returnResult()
                .getResponseBody();

        assertThat(page1).isNotNull();
        assertThat(page1.items()).hasSize(pageSize);
        assertThat(page1.limit()).isEqualTo(pageSize);
        assertThat(page1.hasNext()).isTrue();
        assertThat(page1.nextCursor()).isNotNull();

        // Validar ordenação DESC (mais novos primeiro)
        for (int i = 0; i < page1.items().size() - 1; i++) {
            UUID curr = page1.items().get(i).id();
            UUID next = page1.items().get(i + 1).id();
            assertThat(curr.version()).isEqualTo(7);
            assertThat(curr.compareTo(next))
                    .as("Ordenação decrescente na página 1")
                    .isGreaterThan(0);
        }

        UUID lastIdPage1 = page1.items().get(pageSize - 1).id();
        assertThat(page1.nextCursor()).isEqualTo(lastIdPage1);
        page1.items().forEach(item -> allSeenIds.add(item.id()));

        // 3. Requisita Página 2: limit 5 com cursor da página 1
        UUID cursor1 = page1.nextCursor();
        KeysetPageResponse<ReceivableResponse> page2 = webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/receivables")
                        .queryParam("merchantId", merchantId)
                        .queryParam("cursor", cursor1)
                        .queryParam("limit", pageSize)
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(PAGE_RESPONSE_TYPE)
                .returnResult()
                .getResponseBody();

        assertThat(page2).isNotNull();
        assertThat(page2.items()).hasSize(pageSize);
        assertThat(page2.hasNext()).isTrue();
        assertThat(page2.nextCursor()).isNotNull();

        // Todos os itens da página 2 devem ser estritamente menores que cursor1
        for (ReceivableResponse item : page2.items()) {
            assertThat(item.id().compareTo(cursor1))
                    .as("Item da página 2 deve ser estritamente menor que cursor1")
                    .isLessThan(0);
            assertThat(allSeenIds.add(item.id()))
                    .as("Item %s não deve ser duplicado na página 2", item.id())
                    .isTrue();
        }

        UUID lastIdPage2 = page2.items().get(pageSize - 1).id();
        assertThat(page2.nextCursor()).isEqualTo(lastIdPage2);

        // 4. Requisita Página 3: limit 5 com cursor da página 2
        UUID cursor2 = page2.nextCursor();
        KeysetPageResponse<ReceivableResponse> page3 = webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/receivables")
                        .queryParam("merchantId", merchantId)
                        .queryParam("cursor", cursor2)
                        .queryParam("limit", pageSize)
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(PAGE_RESPONSE_TYPE)
                .returnResult()
                .getResponseBody();

        assertThat(page3).isNotNull();
        assertThat(page3.items()).hasSize(pageSize);
        // Última página: hasNext = false, nextCursor = null
        assertThat(page3.hasNext()).isFalse();
        assertThat(page3.nextCursor()).isNull();

        for (ReceivableResponse item : page3.items()) {
            assertThat(item.id().compareTo(cursor2))
                    .as("Item da página 3 deve ser estritamente menor que cursor2")
                    .isLessThan(0);
            assertThat(allSeenIds.add(item.id()))
                    .as("Item %s não deve ser duplicado na página 3", item.id())
                    .isTrue();
        }

        // 5. Total de itens únicos navegados sem repetição ou omissão: exatamente 15
        assertThat(allSeenIds).hasSize(totalReceivables);
        assertThat(allSeenIds).containsExactlyInAnyOrderElementsOf(createdIds);

        // 6. Requisitar além da última página com cursor do último item da página 3 deve retornar 0 itens e hasNext=false
        UUID cursor3 = page3.items().get(pageSize - 1).id();
        KeysetPageResponse<ReceivableResponse> pageEmpty = webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/receivables")
                        .queryParam("merchantId", merchantId)
                        .queryParam("cursor", cursor3)
                        .queryParam("limit", pageSize)
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(PAGE_RESPONSE_TYPE)
                .returnResult()
                .getResponseBody();

        assertThat(pageEmpty).isNotNull();
        assertThat(pageEmpty.items()).isEmpty();
        assertThat(pageEmpty.hasNext()).isFalse();
        assertThat(pageEmpty.nextCursor()).isNull();
    }
}
