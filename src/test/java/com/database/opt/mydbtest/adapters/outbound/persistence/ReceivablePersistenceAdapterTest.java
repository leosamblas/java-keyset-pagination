package com.database.opt.mydbtest.adapters.outbound.persistence;

import com.database.opt.mydbtest.adapters.outbound.persistence.adapter.ReceivablePersistenceAdapter;
import com.database.opt.mydbtest.domain.model.CardBrand;
import com.database.opt.mydbtest.domain.model.Receivable;
import com.database.opt.mydbtest.domain.model.ReceivableStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ReceivablePersistenceAdapterTest {

    @Autowired
    private ReceivablePersistenceAdapter adapter;

    @Test
    @DisplayName("Deveria persistir recebivel sem ID e retornar com UUID versao 7 gerado nativamente pelo PostgreSQL 18")
    void shouldPersistReceivableWithDatabaseGeneratedUuidV7() {
        Receivable receivable = Receivable.builder()
                .merchantId("merchant-persistence-test")
                .cardBrand(CardBrand.VISA)
                .grossAmount(new BigDecimal("250.00"))
                .feeRate(new BigDecimal("0.0200"))
                .transactionDate(Instant.now())
                .build();

        StepVerifier.create(adapter.save(receivable))
                .assertNext(saved -> {
                    assertThat(saved.getId()).isNotNull();
                    // Comprovação estrita da RFC 9562: UUID versão 7
                    assertThat(saved.getId().version()).isEqualTo(7);
                    assertThat(saved.getMerchantId()).isEqualTo("merchant-persistence-test");
                    assertThat(saved.getGrossAmount()).isEqualByComparingTo("250.00");
                    assertThat(saved.getFeeAmount()).isEqualByComparingTo("5.00");
                    assertThat(saved.getNetAmount()).isEqualByComparingTo("245.00");
                    assertThat(saved.getStatus()).isEqualTo(ReceivableStatus.PENDING);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Deveria buscar recebiveis via Keyset Seek Query com e sem cursor")
    void shouldFindKeysetWithAndWithoutCursor() throws InterruptedException {
        String merchantId = "merchant-keyset-" + UUID.randomUUID();

        // Cadastra 5 recebíveis sequenciais com pequenos intervalos para garantir ordenação temporal
        List<Receivable> createdList = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Receivable r = Receivable.builder()
                    .merchantId(merchantId)
                    .cardBrand(CardBrand.MASTERCARD)
                    .grossAmount(new BigDecimal("100.00").multiply(BigDecimal.valueOf(i)))
                    .feeRate(new BigDecimal("0.0150"))
                    .transactionDate(Instant.now())
                    .build();
            Receivable saved = adapter.save(r).block();
            createdList.add(saved);
            Thread.sleep(5);
        }

        assertThat(createdList).hasSize(5);

        // 1ª Página: limit 3 (busca 3 itens)
        List<Receivable> firstPage = adapter.findKeyset(merchantId, null, 3).collectList().block();
        assertThat(firstPage).isNotNull();
        assertThat(firstPage).hasSize(3);

        // Ordem DESC por UUIDv7: o mais recente (criado por último) vem primeiro
        assertThat(firstPage.get(0).getId().compareTo(firstPage.get(1).getId())).isGreaterThan(0);
        assertThat(firstPage.get(1).getId().compareTo(firstPage.get(2).getId())).isGreaterThan(0);

        // 2ª Página: usando cursor do último item da 1ª página
        UUID cursor = firstPage.get(2).getId();
        List<Receivable> secondPage = adapter.findKeyset(merchantId, cursor, 3).collectList().block();
        assertThat(secondPage).isNotNull();
        assertThat(secondPage).hasSize(2);

        // Itens da página 2 devem ter IDs estritamente menores que o cursor
        for (Receivable r : secondPage) {
            assertThat(r.getId().compareTo(cursor)).isLessThan(0);
        }

        // Não há intersecção entre primeira e segunda páginas
        List<UUID> firstPageIds = firstPage.stream().map(Receivable::getId).toList();
        List<UUID> secondPageIds = secondPage.stream().map(Receivable::getId).toList();
        assertThat(firstPageIds).doesNotContainAnyElementsOf(secondPageIds);
    }
}
