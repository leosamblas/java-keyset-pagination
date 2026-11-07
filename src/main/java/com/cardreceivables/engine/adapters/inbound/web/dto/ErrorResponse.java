package com.cardreceivables.engine.adapters.inbound.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Resposta padronizada para erros da API")
public record ErrorResponse(
        @Schema(description = "Timestamp UTC do momento do erro", example = "2026-11-07T14:35:10Z")
        Instant timestamp,

        @Schema(description = "Código de status HTTP", example = "400")
        int status,

        @Schema(description = "Descrição textual do status HTTP", example = "Bad Request")
        String error,

        @Schema(description = "Mensagem resumida do erro", example = "Validation failed for request")
        String message,

        @Schema(description = "Lista detalhada de campos ou causas da falha", example = "[\"grossAmount: grossAmount must be greater than zero\"]")
        List<String> details
) {}
