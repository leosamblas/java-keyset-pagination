package com.database.opt.mydbtest.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReceivableTest {

    @Test
    @DisplayName("Deveria calcular feeAmount com arredondamento HALF_UP e netAmount com precisão")
    void shouldCalculateFeeAndNetAmountAccurately() {
        // Exemplo: R$ 100,00 com taxa de 2.5% (0.025)
        BigDecimal gross = new BigDecimal("100.00");
        BigDecimal feeRate = new BigDecimal("0.0250");
        Instant txDate = Instant.parse("2026-11-01T12:00:00Z");

        Receivable receivable = Receivable.builder()
                .merchantId("merch-123")
                .cardBrand(CardBrand.VISA)
                .grossAmount(gross)
                .feeRate(feeRate)
                .installments(1)
                .installmentNumber(1)
                .transactionDate(txDate)
                .build();

        // 100.00 * 0.0250 = 2.50
        assertThat(receivable.getFeeAmount()).isEqualByComparingTo("2.50");
        // 100.00 - 2.50 = 97.50
        assertThat(receivable.getNetAmount()).isEqualByComparingTo("97.50");
        // Status padrão
        assertThat(receivable.getStatus()).isEqualTo(ReceivableStatus.PENDING);
        // Data de liquidação D+30
        assertThat(receivable.getSettlementDate())
                .isEqualTo(LocalDate.of(2026, 12, 1));
        // createdAt não nulo
        assertThat(receivable.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Deveria aplicar arredondamento HALF_UP corretamente em valores fracionados de taxa")
    void shouldRoundFeeAmountHalfUp() {
        // Exemplo: R$ 105.55 com taxa de 3.33% (0.0333) -> 105.55 * 0.0333 = 3.514815 -> 3.51
        BigDecimal gross = new BigDecimal("105.55");
        BigDecimal feeRate = new BigDecimal("0.0333");
        Instant txDate = Instant.parse("2026-11-01T12:00:00Z");

        Receivable receivable = Receivable.builder()
                .merchantId("merch-123")
                .cardBrand(CardBrand.MASTERCARD)
                .grossAmount(gross)
                .feeRate(feeRate)
                .transactionDate(txDate)
                .build();

        assertThat(receivable.getFeeAmount()).isEqualByComparingTo("3.51");
        assertThat(receivable.getNetAmount()).isEqualByComparingTo("102.04");
        assertThat(receivable.getGrossAmount()).isEqualTo(receivable.getNetAmount().add(receivable.getFeeAmount()));
    }

    @Test
    @DisplayName("Deveria rejeitar grossAmount nulo ou menor ou igual a zero")
    void shouldRejectInvalidGrossAmount() {
        assertThatThrownBy(() -> Receivable.builder()
                .merchantId("merch-123")
                .cardBrand(CardBrand.VISA)
                .grossAmount(BigDecimal.ZERO)
                .feeRate(new BigDecimal("0.02"))
                .transactionDate(Instant.now())
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Gross amount must be positive");

        assertThatThrownBy(() -> Receivable.builder()
                .merchantId("merch-123")
                .cardBrand(CardBrand.VISA)
                .grossAmount(new BigDecimal("-10.00"))
                .feeRate(new BigDecimal("0.02"))
                .transactionDate(Instant.now())
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Gross amount must be positive");
    }

    @Test
    @DisplayName("Deveria rejeitar feeRate negativo")
    void shouldRejectNegativeFeeRate() {
        assertThatThrownBy(() -> Receivable.builder()
                .merchantId("merch-123")
                .cardBrand(CardBrand.VISA)
                .grossAmount(new BigDecimal("100.00"))
                .feeRate(new BigDecimal("-0.01"))
                .transactionDate(Instant.now())
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Fee rate cannot be negative");
    }

    @Test
    @DisplayName("Deveria considerar dois recebiveis sem ID (id == null) como nao iguais")
    void shouldNotConsiderUnpersistedReceivablesAsEqual() {
        Receivable r1 = Receivable.builder()
                .merchantId("merch-1")
                .cardBrand(CardBrand.VISA)
                .grossAmount(new BigDecimal("100.00"))
                .feeRate(new BigDecimal("0.02"))
                .build();

        Receivable r2 = Receivable.builder()
                .merchantId("merch-1")
                .cardBrand(CardBrand.VISA)
                .grossAmount(new BigDecimal("100.00"))
                .feeRate(new BigDecimal("0.02"))
                .build();

        assertThat(r1.getId()).isNull();
        assertThat(r2.getId()).isNull();
        assertThat(r1).isNotEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }

    @Test
    @DisplayName("Deveria construir KeysetPage com hasNext e nextCursor quando houver itens excedentes")
    void shouldBuildKeysetPageWithNextCursorWhenHasNext() {
        UUID id1 = UUID.fromString("01930777-a123-7456-8abc-def012345678");
        UUID id2 = UUID.fromString("01930777-a122-7456-8abc-def012345678");
        UUID id3 = UUID.fromString("01930777-a121-7456-8abc-def012345678");

        List<TestItem> rawItems = List.of(
                new TestItem(id1, "Item 1"),
                new TestItem(id2, "Item 2"),
                new TestItem(id3, "Item 3")
        );

        int limit = 2;
        KeysetPage<TestItem> page = KeysetPage.of(rawItems, limit, TestItem::id);

        assertThat(page.items()).hasSize(2);
        assertThat(page.items()).containsExactly(rawItems.get(0), rawItems.get(1));
        assertThat(page.limit()).isEqualTo(2);
        assertThat(page.hasNext()).isTrue();
        assertThat(page.nextCursor()).isEqualTo(id2);
    }

    @Test
    @DisplayName("Deveria construir KeysetPage com hasNext false quando não houver itens excedentes")
    void shouldBuildKeysetPageWithoutNextCursorWhenLastPage() {
        UUID id1 = UUID.fromString("01930777-a123-7456-8abc-def012345678");
        UUID id2 = UUID.fromString("01930777-a122-7456-8abc-def012345678");

        List<TestItem> rawItems = List.of(
                new TestItem(id1, "Item 1"),
                new TestItem(id2, "Item 2")
        );

        int limit = 2;
        KeysetPage<TestItem> page = KeysetPage.of(rawItems, limit, TestItem::id);

        assertThat(page.items()).hasSize(2);
        assertThat(page.limit()).isEqualTo(2);
        assertThat(page.hasNext()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    record TestItem(UUID id, String name) {}
}

