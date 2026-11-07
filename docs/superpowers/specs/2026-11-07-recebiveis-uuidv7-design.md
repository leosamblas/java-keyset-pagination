# Design Document: Cadastro de Recebíveis de Cartão com PostgreSQL UUIDv7, Spring Boot Reativo (R2DBC), Arquitetura Hexagonal e Keyset Pagination

- **Data:** 2026-11-07
- **Autor/Equipe:** Pair programming com Antigravity
- **Status:** Aprovado e Revisado (Gaps Mitigados)

---

## 1. Contexto e Motivação (Intent & The Why)

O PostgreSQL 18 introduziu suporte nativo à função `uuidv7()` (RFC 9562). O **UUID versão 7** é um identificador único com ordenação temporal (time-ordered), combinando timestamp Unix de 48 bits com entropia aleatória. Em sistemas de pagamentos e recebíveis de cartão, o UUID v7 evita fragmentação de páginas em índices B-Tree e viabiliza ordenação cronológica direta pela chave primária.

Adicionalmente, consultas paginadas em alto volume com a abordagem tradicional `OFFSET / LIMIT` sofrem degradação de performance $\mathcal{O}(\text{offset})$ e inconsistências por "data drift" (registros pulados ou duplicados devido a inserções simultâneas). A técnica de **Keyset Pagination (Seek / Cursor-based Pagination)** resolve este problema executando buscas em $\mathcal{O}(\text{page\_size})$ diretamente no índice B-Tree.

Graças à natureza temporal monotônica do UUIDv7, **o próprio `id` atua como chave de cursor temporal global**. Eliminamos a necessidade de tuplas compostas como `(created_at, id)`, tornando a busca sequencial por cursor extremamente rápida e limpa no PostgreSQL 18.

Este projeto implementa uma solução de alta performance com **Spring WebFlux**, **Spring Data R2DBC**, **Virtual Threads (Java 25)**, **Flyway** e **Connection Pool Reativo**, estruturado em **Arquitetura Hexagonal (Ports & Adapters)**.

---

## 2. Metas (Goals), Não-Metas (Non-Goals) e Anti-Metas (Anti-Goals)

### Metas (Goals)
1. **Arquitetura Hexagonal Estrita:**
   - **Domínio Puro:** Entidade de domínio `Receivable`, record genérico `KeysetPage<T>` e enums sem acoplamento a Spring ou anotações de persistência.
   - **Portas de Entrada (Driving Ports / Use Cases):** Contratos de negócio (`CreateReceivableUseCase`, `ListReceivablesKeysetUseCase`, etc.).
   - **Portas de Saída (Driven Ports / SPI):** Interface `ReceivableRepositoryPort` definindo o contrato de persistência.
   - **Adaptadores:** Web reativo (WebFlux controller + `GlobalExceptionHandler`) e R2DBC repository como adaptador de saída.
2. **Keyset Pagination de Alta Performance:**
   - Implementar paginação baseada em cursor utilizando o `id` (UUIDv7) como marcador temporal.
   - Segregar consultas de primeira página (`findFirstPage`) e páginas seguintes com cursor (`findNextPage`) para evitar problemas de inferência de tipos nulos e garantir **100% de uso do índice B-tree (`Index Scan`)**.
   - Retornar metadados: itens, `limit`, `nextCursor` e indicador booleano `hasNext` (via estratégia de buscar `limit + 1`).
   - Complexidade constante $\mathcal{O}(\text{limit})$ garantida por seek direto no índice.
3. **Resiliência e Coexistência de Infraestrutura:**
   - Migração automatizada via Flyway na inicialização da aplicação usando conexão JDBC dedicada com flag `spring.flyway.ignore-unsupported-database=true` para compatibilidade total com o PostgreSQL 18.
   - Operação reativa com `r2dbc-pool` padrão do Spring Boot.
4. **Precisão Contábil Rigorosa:**
   - Cálculos financeiros de MDR com precisão `BigDecimal` e arredondamento explícito `RoundingMode.HALF_UP` na escala 2, assegurando a invariante `grossAmount == netAmount + feeAmount`.
5. **Comprovação de UUIDv7:** Testes automatizados verificando versão 7, ordenação cronológica estrita por ID e paginação contínua sem repetições.

### Não-Metas (Non-Goals)
- Integração real com adquirentes externos.
- Autenticação e autorização (OAuth2/JWT) na PoC técnica.

### Anti-Metas (Anti-Goals)
- Não usar `OFFSET` em hipótese alguma para paginação de recebíveis.
- Não passar parâmetros nulos sem tipagem em cláusulas SQL reativas que possam desabilitar o índice B-tree.
- Não poluir o modelo de domínio com anotações de infraestrutura/Spring Data (`@Table`, `@Id`).
- Não usar chamadas JDBC bloqueantes dentro do fluxo reativo da aplicação.

---

## 3. Arquitetura Hexagonal (Estrutura de Pacotes)

```
com.database.opt.mydbtest/
├── domain/                               # NÚCLEO DO DOMÍNIO (Puro Java 25)
│   ├── model/
│   │   ├── Receivable.java               # Modelo de domínio puro com cálculos financeiros
│   │   ├── CardBrand.java                # Enum de bandeiras
│   │   ├── ReceivableStatus.java         # Status (PENDING, SETTLED, etc.)
│   │   └── KeysetPage.java               # Record com itens, limit, nextCursor, hasNext
│   └── ports/
│       ├── in/                           # Driving Ports (Casos de Uso)
│       │   ├── CreateReceivableUseCase.java
│       │   ├── CreateReceivableBatchUseCase.java
│       │   ├── FindReceivableByIdUseCase.java
│       │   └── ListReceivablesKeysetUseCase.java
│       └── out/                          # Driven Ports (SPI Persistência)
│           └── ReceivableRepositoryPort.java
├── application/                          # SERVIÇOS DA APLICAÇÃO
│   └── service/
│       └── ReceivableApplicationService.java # Implementa Use Cases usando Port Out
├── adapters/                             # ADAPTADORES (Infraestrutura)
│   ├── inbound/                          # Adaptadores de Entrada (WebFlux HTTP)
│   │   └── web/
│   │       ├── ReceivableController.java # Endpoints REST reativos
│   │       ├── GlobalExceptionHandler.java # Tratamento de erros HTTP (400, 404, 500)
│   │       ├── dto/
│   │       │   ├── CreateReceivableRequest.java
│   │       │   ├── ReceivableResponse.java
│   │       │   └── KeysetPageResponse.java
│   │       └── mapper/
│   │           └── ReceivableWebMapper.java
│   └── outbound/                         # Adaptadores de Saída (Persistência R2DBC)
│       └── persistence/
│           ├── entity/
│           │   └── ReceivableEntity.java # Entidade R2DBC (@Table + Persistable<UUID>)
│           ├── repository/
│           │   └── SpringDataReceivableRepository.java # ReactiveCrudRepository com Seek Queries
│           ├── adapter/
│           │   └── ReceivablePersistenceAdapter.java   # Implementa ReceivableRepositoryPort
│           └── mapper/
│               └── ReceivablePersistenceMapper.java
└── infrastructure/                       # CONFIGURAÇÕES DE FRAMEWORK
    └── config/
        └── BeanConfiguration.java        # Injeção de dependências do domínio e aplicação
```

---

## 4. Keyset Pagination Otimizada com UUIDv7

### 4.1. Mecânica de Consulta (Seek Query Segregada)

Para evitar erros de inferência de tipos nulos (`could not determine data type of parameter`) e assegurar que o PostgreSQL 18 utilize o índice B-tree `idx_receivables_merchant_id_desc` em sua capacidade máxima:

1. **Primeira página (`cursor == null`):**
   ```sql
   -- Com merchant_id:
   SELECT * FROM receivables 
   WHERE merchant_id = :merchantId 
   ORDER BY id DESC 
   LIMIT :fetchLimit;

   -- Sem merchant_id:
   SELECT * FROM receivables 
   ORDER BY id DESC 
   LIMIT :fetchLimit;
   ```

2. **Páginas subsequentes (`cursor != null`):**
   ```sql
   -- Com merchant_id:
   SELECT * FROM receivables 
   WHERE merchant_id = :merchantId AND id < :cursor 
   ORDER BY id DESC 
   LIMIT :fetchLimit;

   -- Sem merchant_id:
   SELECT * FROM receivables 
   WHERE id < :cursor 
   ORDER BY id DESC 
   LIMIT :fetchLimit;
   ```

### 4.2. Detecção de Próxima Página (`hasNext`) e Cursor
- Ao buscar `limit + 1` itens:
  - Se retornar `limit + 1`, define `hasNext = true` e o `nextCursor` recebe o `id` do último item da lista fatiada de tamanho `limit`.
  - Se retornar $\le limit$, define `hasNext = false` e `nextCursor = null`.
- Zero chamadas a `COUNT(*)`.

---

## 5. Esquema Flyway (`V1__create_receivables_table.sql`)

```sql
CREATE TABLE IF NOT EXISTS receivables (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    merchant_id VARCHAR(64) NOT NULL,
    card_brand VARCHAR(32) NOT NULL,
    gross_amount NUMERIC(15, 2) NOT NULL,
    fee_rate NUMERIC(5, 4) NOT NULL,
    fee_amount NUMERIC(15, 2) NOT NULL,
    net_amount NUMERIC(15, 2) NOT NULL,
    installments INT NOT NULL DEFAULT 1,
    installment_number INT NOT NULL DEFAULT 1,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    transaction_date TIMESTAMPTZ NOT NULL,
    settlement_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

-- Índice composto para consultas por lojista ordenadas por ID descrescente (UUIDv7)
CREATE INDEX IF NOT EXISTS idx_receivables_merchant_id_desc 
    ON receivables (merchant_id, id DESC);
```

---

## 6. Configurações de Coexistência Flyway (JDBC) + R2DBC Pool

No `application.properties`:
```properties
spring.threads.virtual.enabled=true

# R2DBC (Reativo com Pool padrão)
spring.r2dbc.url=r2dbc:postgresql://localhost:5432/postgres
spring.r2dbc.username=postgres
spring.r2dbc.password=developer
spring.r2dbc.pool.enabled=true

# Flyway (Bootstrap via JDBC)
spring.flyway.enabled=true
spring.flyway.url=jdbc:postgresql://localhost:5432/postgres
spring.flyway.user=postgres
spring.flyway.password=developer
spring.flyway.locations=classpath:db/migration
spring.flyway.ignore-unsupported-database=true
```

---

## 7. API e Endpoints HTTP Reativos

Base path: `/api/v1/receivables`

| Método | Caminho | Parâmetros | Descrição | Retorno |
|---|---|---|---|---|
| `POST` | `/api/v1/receivables` | - | Cadastra um recebível individual | `Mono<ResponseEntity<ReceivableResponse>>` (201 Created) |
| `POST` | `/api/v1/receivables/batch` | - | Cadastra recebíveis em lote | `Flux<ReceivableResponse>` (201 Created) |
| `GET` | `/api/v1/receivables/{id}` | - | Busca recebível por ID (UUID) | `Mono<ResponseEntity<ReceivableResponse>>` (200 / 404) |
| `GET` | `/api/v1/receivables` | `merchantId` (opcional), `cursor` (UUID opcional), `limit` (int, default 20) | Consulta paginada via Keyset Pagination | `Mono<KeysetPageResponse<ReceivableResponse>>` (200 OK) |
