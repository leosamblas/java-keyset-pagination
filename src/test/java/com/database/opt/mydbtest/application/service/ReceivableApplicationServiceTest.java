package com.database.opt.mydbtest.application.service;

import com.database.opt.mydbtest.domain.model.CardBrand;
import com.database.opt.mydbtest.domain.model.KeysetPage;
import com.database.opt.mydbtest.domain.model.Receivable;
import com.database.opt.mydbtest.domain.model.ReceivableStatus;
import com.database.opt.mydbtest.domain.ports.in.CreateReceivableCommand;
import com.database.opt.mydbtest.domain.ports.out.ReceivableRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceivableApplicationServiceTest {

    @Mock
    private ReceivableRepositoryPort repositoryPort;

    private ReceivableApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ReceivableApplicationService(repositoryPort);
    }

    @Test
    @DisplayName("Deveria criar recebivel a partir do comando e persistir")
    void shouldCreateReceivableFromCommand() {
        CreateReceivableCommand command = new CreateReceivableCommand(
                "merch-1",
                CardBrand.VISA,
                new BigDecimal("100.00"),
                new BigDecimal("0.0250"),
                1,
                1,
                Instant.now()
        );

        UUID generatedId = UUID.randomUUID();
        Receivable saved = Receivable.builder()
                .id(generatedId)
                .merchantId(command.merchantId())
                .cardBrand(command.cardBrand())
                .grossAmount(command.grossAmount())
                .feeRate(command.feeRate())
                .installments(command.installments())
                .installmentNumber(command.installmentNumber())
                .transactionDate(command.transactionDate())
                .build();

        when(repositoryPort.save(any(Receivable.class))).thenReturn(Mono.just(saved));

        StepVerifier.create(service.create(command))
                .assertNext(result -> {
                    assertThat(result.getId()).isEqualTo(generatedId);
                    assertThat(result.getMerchantId()).isEqualTo("merch-1");
                    assertThat(result.getFeeAmount()).isEqualByComparingTo("2.50");
                    assertThat(result.getNetAmount()).isEqualByComparingTo("97.50");
                })
                .verifyComplete();

        verify(repositoryPort).save(any(Receivable.class));
    }

    @Test
    @DisplayName("Deveria buscar recebivel por id")
    void shouldFindReceivableById() {
        UUID id = UUID.randomUUID();
        Receivable existing = Receivable.builder()
                .id(id)
                .merchantId("merch-1")
                .cardBrand(CardBrand.MASTERCARD)
                .grossAmount(new BigDecimal("50.00"))
                .feeRate(new BigDecimal("0.02"))
                .build();

        when(repositoryPort.findById(id)).thenReturn(Mono.just(existing));

        StepVerifier.create(service.findById(id))
                .assertNext(result -> assertThat(result.getId()).isEqualTo(id))
                .verifyComplete();

        verify(repositoryPort).findById(id);
    }

    @Test
    @DisplayName("Deveria paginar via keyset requisitando limit + 1 e montando KeysetPage com nextCursor")
    void shouldListKeysetWithNextPage() {
        String merchantId = "merch-1";
        UUID cursor = UUID.randomUUID();
        int limit = 2;

        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();

        Receivable r1 = Receivable.builder().id(id1).merchantId(merchantId).cardBrand(CardBrand.VISA).grossAmount(new BigDecimal("10.00")).feeRate(new BigDecimal("0.01")).build();
        Receivable r2 = Receivable.builder().id(id2).merchantId(merchantId).cardBrand(CardBrand.VISA).grossAmount(new BigDecimal("20.00")).feeRate(new BigDecimal("0.01")).build();
        Receivable r3 = Receivable.builder().id(id3).merchantId(merchantId).cardBrand(CardBrand.VISA).grossAmount(new BigDecimal("30.00")).feeRate(new BigDecimal("0.01")).build();

        // O repositório retorna 3 itens (limit + 1)
        when(repositoryPort.findKeyset(eq(merchantId), eq(cursor), eq(limit + 1)))
                .thenReturn(Flux.just(r1, r2, r3));

        StepVerifier.create(service.listKeyset(merchantId, cursor, limit))
                .assertNext(page -> {
                    assertThat(page.items()).hasSize(2);
                    assertThat(page.items()).containsExactly(r1, r2);
                    assertThat(page.limit()).isEqualTo(2);
                    assertThat(page.hasNext()).isTrue();
                    assertThat(page.nextCursor()).isEqualTo(id2);
                })
                .verifyComplete();

        verify(repositoryPort).findKeyset(merchantId, cursor, limit + 1);
    }

    @Test
    @DisplayName("Deveria limitar o limit maximo a 100 para proteger memoria do servidor")
    void shouldClampLimitToMaximumAllowed() {
        String merchantId = "merch-1";
        when(repositoryPort.findKeyset(eq(merchantId), any(), eq(101)))
                .thenReturn(Flux.empty());

        StepVerifier.create(service.listKeyset(merchantId, null, 1000))
                .assertNext(page -> {
                    assertThat(page.limit()).isEqualTo(100);
                    assertThat(page.hasNext()).isFalse();
                })
                .verifyComplete();

        verify(repositoryPort).findKeyset(merchantId, null, 101);
    }

    @Test
    @DisplayName("Deveria paginar via keyset e indicar hasNext = false na última página")
    void shouldListKeysetOnLastPage() {
        String merchantId = "merch-1";
        int limit = 3;

        UUID id1 = UUID.randomUUID();
        Receivable r1 = Receivable.builder().id(id1).merchantId(merchantId).cardBrand(CardBrand.VISA).grossAmount(new BigDecimal("10.00")).feeRate(new BigDecimal("0.01")).build();

        when(repositoryPort.findKeyset(eq(merchantId), eq(null), eq(limit + 1)))
                .thenReturn(Flux.just(r1));

        StepVerifier.create(service.listKeyset(merchantId, null, limit))
                .assertNext(page -> {
                    assertThat(page.items()).hasSize(1);
                    assertThat(page.hasNext()).isFalse();
                    assertThat(page.nextCursor()).isNull();
                })
                .verifyComplete();
    }
}

