package com.cardreceivables.engine.adapters.inbound.web.mapper;

import com.cardreceivables.engine.adapters.inbound.web.dto.CreateReceivableRequest;
import com.cardreceivables.engine.adapters.inbound.web.dto.KeysetPageResponse;
import com.cardreceivables.engine.adapters.inbound.web.dto.ReceivableResponse;
import com.cardreceivables.engine.domain.model.KeysetPage;
import com.cardreceivables.engine.domain.model.Receivable;
import com.cardreceivables.engine.domain.ports.in.CreateReceivableCommand;

import java.util.List;

public final class ReceivableWebMapper {

    private ReceivableWebMapper() {}

    public static CreateReceivableCommand toCommand(CreateReceivableRequest req) {
        if (req == null) return null;
        return new CreateReceivableCommand(
                req.merchantId(),
                req.cardBrand(),
                req.grossAmount(),
                req.feeRate(),
                req.installments(),
                req.installmentNumber(),
                req.transactionDate()
        );
    }

    public static ReceivableResponse toResponse(Receivable r) {
        if (r == null) return null;
        return new ReceivableResponse(
                r.getId(),
                r.getMerchantId(),
                r.getCardBrand(),
                r.getGrossAmount(),
                r.getFeeRate(),
                r.getFeeAmount(),
                r.getNetAmount(),
                r.getInstallments(),
                r.getInstallmentNumber(),
                r.getStatus(),
                r.getTransactionDate(),
                r.getSettlementDate(),
                r.getCreatedAt()
        );
    }

    public static KeysetPageResponse<ReceivableResponse> toPageResponse(KeysetPage<Receivable> page) {
        if (page == null) return null;
        List<ReceivableResponse> responses = page.items().stream()
                .map(ReceivableWebMapper::toResponse)
                .toList();
        return new KeysetPageResponse<>(
                responses,
                page.limit(),
                page.nextCursor(),
                page.hasNext()
        );
    }
}

