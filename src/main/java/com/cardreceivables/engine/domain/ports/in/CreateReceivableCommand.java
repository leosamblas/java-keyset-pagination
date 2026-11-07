package com.cardreceivables.engine.domain.ports.in;

import com.cardreceivables.engine.domain.model.CardBrand;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

@Builder
public record CreateReceivableCommand(
        String merchantId,
        CardBrand cardBrand,
        BigDecimal grossAmount,
        BigDecimal feeRate,
        Integer installments,
        Integer installmentNumber,
        Instant transactionDate
) {}

