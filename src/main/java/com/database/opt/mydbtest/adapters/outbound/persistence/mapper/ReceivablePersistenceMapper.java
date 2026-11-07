package com.database.opt.mydbtest.adapters.outbound.persistence.mapper;

import com.database.opt.mydbtest.adapters.outbound.persistence.entity.ReceivableEntity;
import com.database.opt.mydbtest.domain.model.CardBrand;
import com.database.opt.mydbtest.domain.model.Receivable;
import com.database.opt.mydbtest.domain.model.ReceivableStatus;

import java.time.Instant;

public final class ReceivablePersistenceMapper {

    private ReceivablePersistenceMapper() {}

    public static ReceivableEntity toEntity(Receivable domain) {
        if (domain == null) return null;

        ReceivableEntity entity = new ReceivableEntity();
        entity.setId(domain.getId());
        entity.setMerchantId(domain.getMerchantId());
        entity.setCardBrand(domain.getCardBrand() != null ? domain.getCardBrand().name() : null);
        entity.setGrossAmount(domain.getGrossAmount());
        entity.setFeeRate(domain.getFeeRate());
        entity.setFeeAmount(domain.getFeeAmount());
        entity.setNetAmount(domain.getNetAmount());
        entity.setInstallments(domain.getInstallments());
        entity.setInstallmentNumber(domain.getInstallmentNumber());
        entity.setStatus(domain.getStatus() != null ? domain.getStatus().name() : ReceivableStatus.PENDING.name());
        entity.setTransactionDate(domain.getTransactionDate());
        entity.setSettlementDate(domain.getSettlementDate());
        entity.setCreatedAt(domain.getCreatedAt() != null ? domain.getCreatedAt() : Instant.now());
        entity.setNew(domain.getId() == null);

        return entity;
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

