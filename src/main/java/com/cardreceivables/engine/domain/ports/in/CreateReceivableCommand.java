package com.cardreceivables.engine.domain.ports.in;

import com.cardreceivables.engine.domain.model.CardBrand;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateReceivableCommand(
        String merchantId,
        CardBrand cardBrand,
        BigDecimal grossAmount,
        BigDecimal feeRate,
        Integer installments,
        Integer installmentNumber,
        Instant transactionDate
) {}

