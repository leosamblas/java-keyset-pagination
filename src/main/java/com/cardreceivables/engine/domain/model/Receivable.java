package com.cardreceivables.engine.domain.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

@Getter
@ToString
public class Receivable {

    private final UUID id;
    private final String merchantId;
    private final CardBrand cardBrand;
    private final BigDecimal grossAmount;
    private final BigDecimal feeRate;
    private final BigDecimal feeAmount;
    private final BigDecimal netAmount;
    private final Integer installments;
    private final Integer installmentNumber;
    private final ReceivableStatus status;
    private final Instant transactionDate;
    private final LocalDate settlementDate;
    private final Instant createdAt;

    @Builder
    public Receivable(
            UUID id,
            String merchantId,
            CardBrand cardBrand,
            BigDecimal grossAmount,
            BigDecimal feeRate,
            BigDecimal feeAmount,
            BigDecimal netAmount,
            Integer installments,
            Integer installmentNumber,
            ReceivableStatus status,
            Instant transactionDate,
            LocalDate settlementDate,
            Instant createdAt
    ) {
        if (merchantId == null || merchantId.isBlank()) {
            throw new IllegalArgumentException("Merchant ID must not be null or blank");
        }
        if (cardBrand == null) {
            throw new IllegalArgumentException("Card brand must not be null");
        }
        if (grossAmount == null || grossAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Gross amount must be positive");
        }
        if (feeRate == null || feeRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Fee rate cannot be negative");
        }

        this.id = id;
        this.merchantId = merchantId;
        this.cardBrand = cardBrand;
        this.grossAmount = grossAmount.setScale(2, RoundingMode.HALF_UP);
        this.feeRate = feeRate;

        BigDecimal calculatedFee = (feeAmount != null)
                ? feeAmount.setScale(2, RoundingMode.HALF_UP)
                : this.grossAmount.multiply(feeRate).setScale(2, RoundingMode.HALF_UP);
        this.feeAmount = calculatedFee;

        this.netAmount = (netAmount != null)
                ? netAmount.setScale(2, RoundingMode.HALF_UP)
                : this.grossAmount.subtract(this.feeAmount);

        this.installments = (installments != null && installments > 0) ? installments : 1;
        this.installmentNumber = (installmentNumber != null && installmentNumber > 0) ? installmentNumber : 1;
        this.status = (status != null) ? status : ReceivableStatus.PENDING;

        Instant txDate = (transactionDate != null) ? transactionDate : Instant.now();
        this.transactionDate = txDate;
        this.settlementDate = (settlementDate != null)
                ? settlementDate
                : txDate.atZone(ZoneOffset.UTC).toLocalDate().plusDays(30);

        this.createdAt = (createdAt != null) ? createdAt : Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Receivable that = (Receivable) o;
        return this.id != null && Objects.equals(this.id, that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : getClass().hashCode();
    }
}
