package com.cardreceivables.engine.adapters.outbound.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Table("receivables")
public class ReceivableEntity implements Persistable<UUID> {

    @Id
    @Column("id")
    private UUID id;

    @Column("merchant_id")
    private String merchantId;

    @Column("card_brand")
    private String cardBrand;

    @Column("gross_amount")
    private BigDecimal grossAmount;

    @Column("fee_rate")
    private BigDecimal feeRate;

    @Column("fee_amount")
    private BigDecimal feeAmount;

    @Column("net_amount")
    private BigDecimal netAmount;

    @Column("installments")
    private Integer installments;

    @Column("installment_number")
    private Integer installmentNumber;

    @Column("status")
    private String status;

    @Column("transaction_date")
    private Instant transactionDate;

    @Column("settlement_date")
    private LocalDate settlementDate;

    @Column("created_at")
    private Instant createdAt;

    @Transient
    private boolean isNew = false;

    public ReceivableEntity() {
    }

    public ReceivableEntity(
            UUID id,
            String merchantId,
            String cardBrand,
            BigDecimal grossAmount,
            BigDecimal feeRate,
            BigDecimal feeAmount,
            BigDecimal netAmount,
            Integer installments,
            Integer installmentNumber,
            String status,
            Instant transactionDate,
            LocalDate settlementDate,
            Instant createdAt
    ) {
        this.id = id;
        this.merchantId = merchantId;
        this.cardBrand = cardBrand;
        this.grossAmount = grossAmount;
        this.feeRate = feeRate;
        this.feeAmount = feeAmount;
        this.netAmount = netAmount;
        this.installments = installments;
        this.installmentNumber = installmentNumber;
        this.status = status;
        this.transactionDate = transactionDate;
        this.settlementDate = settlementDate;
        this.createdAt = createdAt;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    @Override
    public boolean isNew() {
        return id == null || isNew;
    }

    public void setNew(boolean isNew) {
        this.isNew = isNew;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }

    public String getCardBrand() {
        return cardBrand;
    }

    public void setCardBrand(String cardBrand) {
        this.cardBrand = cardBrand;
    }

    public BigDecimal getGrossAmount() {
        return grossAmount;
    }

    public void setGrossAmount(BigDecimal grossAmount) {
        this.grossAmount = grossAmount;
    }

    public BigDecimal getFeeRate() {
        return feeRate;
    }

    public void setFeeRate(BigDecimal feeRate) {
        this.feeRate = feeRate;
    }

    public BigDecimal getFeeAmount() {
        return feeAmount;
    }

    public void setFeeAmount(BigDecimal feeAmount) {
        this.feeAmount = feeAmount;
    }

    public BigDecimal getNetAmount() {
        return netAmount;
    }

    public void setNetAmount(BigDecimal netAmount) {
        this.netAmount = netAmount;
    }

    public Integer getInstallments() {
        return installments;
    }

    public void setInstallments(Integer installments) {
        this.installments = installments;
    }

    public Integer getInstallmentNumber() {
        return installmentNumber;
    }

    public void setInstallmentNumber(Integer installmentNumber) {
        this.installmentNumber = installmentNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(Instant transactionDate) {
        this.transactionDate = transactionDate;
    }

    public LocalDate getSettlementDate() {
        return settlementDate;
    }

    public void setSettlementDate(LocalDate settlementDate) {
        this.settlementDate = settlementDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

