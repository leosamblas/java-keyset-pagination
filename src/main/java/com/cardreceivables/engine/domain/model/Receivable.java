package com.cardreceivables.engine.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

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

    public UUID getId() {
        return id;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public CardBrand getCardBrand() {
        return cardBrand;
    }

    public BigDecimal getGrossAmount() {
        return grossAmount;
    }

    public BigDecimal getFeeRate() {
        return feeRate;
    }

    public BigDecimal getFeeAmount() {
        return feeAmount;
    }

    public BigDecimal getNetAmount() {
        return netAmount;
    }

    public Integer getInstallments() {
        return installments;
    }

    public Integer getInstallmentNumber() {
        return installmentNumber;
    }

    public ReceivableStatus getStatus() {
        return status;
    }

    public Instant getTransactionDate() {
        return transactionDate;
    }

    public LocalDate getSettlementDate() {
        return settlementDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private String merchantId;
        private CardBrand cardBrand;
        private BigDecimal grossAmount;
        private BigDecimal feeRate;
        private BigDecimal feeAmount;
        private BigDecimal netAmount;
        private Integer installments = 1;
        private Integer installmentNumber = 1;
        private ReceivableStatus status = ReceivableStatus.PENDING;
        private Instant transactionDate;
        private LocalDate settlementDate;
        private Instant createdAt;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder merchantId(String merchantId) {
            this.merchantId = merchantId;
            return this;
        }

        public Builder cardBrand(CardBrand cardBrand) {
            this.cardBrand = cardBrand;
            return this;
        }

        public Builder grossAmount(BigDecimal grossAmount) {
            this.grossAmount = grossAmount;
            return this;
        }

        public Builder feeRate(BigDecimal feeRate) {
            this.feeRate = feeRate;
            return this;
        }

        public Builder feeAmount(BigDecimal feeAmount) {
            this.feeAmount = feeAmount;
            return this;
        }

        public Builder netAmount(BigDecimal netAmount) {
            this.netAmount = netAmount;
            return this;
        }

        public Builder installments(Integer installments) {
            this.installments = installments;
            return this;
        }

        public Builder installmentNumber(Integer installmentNumber) {
            this.installmentNumber = installmentNumber;
            return this;
        }

        public Builder status(ReceivableStatus status) {
            this.status = status;
            return this;
        }

        public Builder transactionDate(Instant transactionDate) {
            this.transactionDate = transactionDate;
            return this;
        }

        public Builder settlementDate(LocalDate settlementDate) {
            this.settlementDate = settlementDate;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Receivable build() {
            return new Receivable(
                    id,
                    merchantId,
                    cardBrand,
                    grossAmount,
                    feeRate,
                    feeAmount,
                    netAmount,
                    installments,
                    installmentNumber,
                    status,
                    transactionDate,
                    settlementDate,
                    createdAt
            );
        }
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

