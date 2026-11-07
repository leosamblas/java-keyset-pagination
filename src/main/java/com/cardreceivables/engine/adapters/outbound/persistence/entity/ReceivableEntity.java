package com.cardreceivables.engine.adapters.outbound.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
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
    @Builder.Default
    private boolean isNew = false;

    @Override
    public boolean isNew() {
        return id == null || isNew;
    }

    public void setNew(boolean isNew) {
        this.isNew = isNew;
    }
}
