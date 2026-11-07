package com.database.opt.mydbtest.adapters.inbound.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "Estrutura de resposta para Keyset (Seek) Pagination de alta performance")
public record KeysetPageResponse<T>(
        @Schema(description = "Lista de itens retornados na página atual")
        List<T> items,

        @Schema(description = "Limite solicitado por página (máximo: 100)", example = "20")
        int limit,

        @Schema(description = "Cursor UUIDv7 para buscar a próxima página (null se for a última página)", example = "01930777-a120-7456-8abc-def012345678")
        UUID nextCursor,

        @Schema(description = "Indica se existem mais páginas a serem buscadas", example = "true")
        boolean hasNext
) {}
