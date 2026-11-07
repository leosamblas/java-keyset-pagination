package com.cardreceivables.engine.adapters.outbound.persistence.mapper;

import com.cardreceivables.engine.adapters.outbound.persistence.entity.ReceivableEntity;
import com.cardreceivables.engine.domain.model.CardBrand;
import com.cardreceivables.engine.domain.model.Receivable;
import com.cardreceivables.engine.domain.model.ReceivableStatus;

import java.time.Instant;

public final class ReceivablePersistenceMapper {

    private ReceivablePersistenceMapper() {}

    public static ReceivableEntity toEntity(Receivable domain) {
        if (domain == null) return null;

        return ReceivableEntity.builder()
                .id(domain.getId())
                .merchantId(domain.getMerchantId())
                .cardBrand(domain.getCardBrand() != null ? domain.getCardBrand().name() : null)
                .grossAmount(domain.getGrossAmount())
                .feeRate(domain.getFeeRate())
                .feeAmount(domain.getFeeAmount())
                .netAmount(domain.getNetAmount())
                .installments(domain.getInstallments())
                .installmentNumber(domain.getInstallmentNumber())
                .status(domain.getStatus() != null ? domain.getStatus().name() : ReceivableStatus.PENDING.name())
                .transactionDate(domain.getTransactionDate())
                .settlementDate(domain.getSettlementDate())
                .createdAt(domain.getCreatedAt() != null ? domain.getCreatedAt() : Instant.now())
                .isNew(domain.getId() == null)
                .build();
    }

    public static Receivable toDomain(ReceivableEntity entity) {
        if (entity == null) return null;

        return Receivable.builder()
                .id(entity.getId())
                .merchantId(entity.getMerchantId())
                .cardBrand(entity.getCardBrand() != null ? CardBrand.valueOf(entity.getCardBrand()) : null)
                .grossAmount(entity.getGrossAmount())
                .feeRate(entity.getFeeRate())
                .feeAmount(entity.getFeeAmount())
                .netAmount(entity.getNetAmount())
                .installments(entity.getInstallments())
                .installmentNumber(entity.getInstallmentNumber())
                .status(entity.getStatus() != null ? ReceivableStatus.valueOf(entity.getStatus()) : ReceivableStatus.PENDING)
                .transactionDate(entity.getTransactionDate())
                .settlementDate(entity.getSettlementDate())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}

