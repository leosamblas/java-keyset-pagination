# Cadastro de Recebíveis com PostgreSQL UUIDv7, Spring WebFlux/R2DBC, Arquitetura Hexagonal e Keyset Pagination Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implementar o cadastro e consulta reativa de recebíveis de cartão de crédito/débito utilizando Spring WebFlux e Spring Data R2DBC em Arquitetura Hexagonal (Ports & Adapters) com Keyset Pagination de alta performance, comprovando a geração nativa e ordenação temporal de UUID v7 no PostgreSQL 18 gerenciado por Flyway na inicialização com pool de conexões padrão.

**Architecture:** Arquitetura Hexagonal (Ports & Adapters) com núcleo de domínio puro (Java 25) isolado de frameworks. Camada de aplicação com Casos de Uso (Portas de Entrada) e Portas de Saída para persistência. Adaptador de Entrada Web reativo (Spring WebFlux + GlobalExceptionHandler) e Adaptador de Saída de Persistência reativo (Spring Data R2DBC + r2dbc-pool). Keyset Pagination segregada sem OFFSET utilizando o UUIDv7 como cursor temporal monotônico indexado. Migração DDL executada na inicialização pelo Flyway via conexão JDBC dedicada com bypass de versão para PostgreSQL 18.

**Tech Stack:** Java 25, Spring Boot 4.x (WebFlux, Data R2DBC, Starter JDBC, Validation), PostgreSQL 18.6, R2DBC PostgreSQL Driver, R2DBC Pool, Flyway Community (PostgreSQL), Project Reactor, JUnit 5, AssertJ.

**Spec:** [`docs/superpowers/specs/2026-11-07-recebiveis-uuidv7-design.md`](file:///c:/Users/leosa/Documents/Projects/mydbtest/docs/superpowers/specs/2026-11-07-recebiveis-uuidv7-design.md)

## Global Constraints

- Java 25 LTS toolchain.
- Arquitetura Hexagonal estrita: domínio (`domain.*`) e aplicação (`application.*`) sem anotações de Spring (`@Service`, `@Component`, `@Table`, etc.).
- Virtual threads habilitadas via `spring.threads.virtual.enabled=true`.
- PostgreSQL 18.6 em container Docker `postgresql-service` (porta 5432, banco `postgres`, usuário `postgres`, senha `developer`).
- Chave primária gerada exclusivamente pelo PostgreSQL via `DEFAULT uuidv7()`.
- Consultas paginadas implementadas exclusivamente via **Keyset (Seek) Pagination**, segregando busca inicial de buscas com cursor para garantir busca direta no índice B-tree em tempo $\mathcal{O}(\text{limit})$.
- Pool de conexões R2DBC ativo com configurações padrão (`spring.r2dbc.pool.enabled=true`).
- Compatibilidade preventiva com PostgreSQL 18 no Flyway configurada via `spring.flyway.ignore-unsupported-database=true`.
- Nenhuma chamada síncrona bloqueante dentro do fluxo de negócio/dados da aplicação (reatividade ponta a ponta com `Mono`/`Flux`).

## Review Focus

- Geração de UUID v7 pelo banco sem fornecimento de ID no payload: a entidade R2DBC deve omitir o ID no INSERT, permitindo ao PostgreSQL preencher `id` com `uuidv7()`.
- Prevenção de erro de `NOT NULL` em `created_at`: o Java inicializa `createdAt = Instant.now()` caso venha nulo antes de persistir, evitando envio de `NULL` explícito ao banco.
- Keyset Seek Query sem erro de tipagem nula: segregação entre busca da primeira página e buscas com cursor, garantindo uso do índice `idx_receivables_merchant_id_desc` e eliminando o erro `could not determine data type of parameter`.
- Tratamento de Erros no WebFlux: `GlobalExceptionHandler` interceptando `WebExchangeBindException` e `IllegalArgumentException`, retornando HTTP 400 estruturado com detalhes do erro.
- Isolamento de Domínio na Arquitetura Hexagonal: entidades do domínio puro (`Receivable`, `KeysetPage`) sem anotações de persistência ou Spring.
- Consistência Contábil: `feeAmount = grossAmount.multiply(feeRate).setScale(2, RoundingMode.HALF_UP)` e `netAmount = grossAmount.subtract(feeAmount)`.

---

### Task 1: Configuração de Dependências e Infraestrutura (R2DBC Pool, Flyway, Virtual Threads)

**Files:**
- Modify: `build.gradle`
- Modify: `src/main/resources/application.properties`

**Interfaces:**
- Consumes: PostgreSQL 18.6 container em `localhost:5432`.
- Produces: Ambiente configurado com R2DBC, pool de conexões padrão, Starter JDBC para Flyway, WebFlux e Virtual Threads.

- [ ] **Step 1: Atualizar `build.gradle` com dependências reativas, pool e Flyway**
  - Remover `spring-boot-starter-data-jpa` e `spring-boot-starter-data-jpa-test`.
  - Adicionar `spring-boot-starter-data-r2dbc`.
  - Adicionar driver reativo `org.postgresql:r2dbc-postgresql`.
  - Adicionar connection pool reativo `io.r2dbc:r2dbc-pool`.
  - Adicionar `org.springframework.boot:spring-boot-starter-jdbc` (suporte ao DataSource JDBC para o Flyway).
  - Adicionar dependências Flyway: `org.flywaydb:flyway-core` e `org.flywaydb:flyway-database-postgresql`.
  - Manter `org.postgresql:postgresql` como `runtimeOnly` (driver JDBC para o Flyway).
  - Adicionar `io.projectreactor:reactor-test` para testes reativos com `StepVerifier`.

- [ ] **Step 2: Configurar `src/main/resources/application.properties`**
  - Habilitar virtual threads: `spring.threads.virtual.enabled=true`.
  - Configurar conexão R2DBC e pool padrão:
    ```properties
    spring.r2dbc.url=r2dbc:postgresql://localhost:5432/postgres
    spring.r2dbc.username=postgres
    spring.r2dbc.password=developer
    spring.r2dbc.pool.enabled=true
    ```
  - Configurar Flyway com suporte a PostgreSQL 18:
    ```properties
    spring.flyway.enabled=true
    spring.flyway.url=jdbc:postgresql://localhost:5432/postgres
    spring.flyway.user=postgres
    spring.flyway.password=developer
    spring.flyway.locations=classpath:db/migration
    spring.flyway.ignore-unsupported-database=true
    ```

- [ ] **Step 3: Verificar compilação do Gradle**
  - Executar: `.\gradlew.bat compileJava compileTestJava`
  - Esperado: BUILD SUCCESSFUL.

---

### Task 2: Migração Flyway com UUIDv7 Nativo do PostgreSQL 18 e Índices de Seek

**Files:**
- Create: `src/main/resources/db/migration/V1__create_receivables_table.sql`
- Modify: `src/test/java/com/database/opt/mydbtest/MydbtestApplicationTests.java`

**Interfaces:**
- Consumes: PostgreSQL 18.6 com suporte a `uuidv7()`.
- Produces: Tabela `receivables` com chave primária UUID, `DEFAULT uuidv7()` e índice composto `idx_receivables_merchant_id_desc`.

- [ ] **Step 1: Criar o script de migração Flyway**
  - Criar `src/main/resources/db/migration/V1__create_receivables_table.sql`:
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

    CREATE INDEX IF NOT EXISTS idx_receivables_merchant_id_desc 
        ON receivables (merchant_id, id DESC);
    ```

- [ ] **Step 2: Executar teste de inicialização do contexto Spring**
  - Executar: `.\gradlew.bat test --tests com.database.opt.mydbtest.MydbtestApplicationTests`
  - Esperado: O teste `contextLoads()` executa com sucesso, sem erro de `Unsupported Database: PostgreSQL 18`, e a migração `V1__create_receivables_table.sql` é aplicada.

- [ ] **Step 3: Validar a criação da tabela no PostgreSQL**
  - Executar: `docker exec postgresql-service psql -U postgres -d postgres -c "\d receivables"`
  - Esperado: Coluna `id` do tipo `uuid` com `DEFAULT uuidv7()` e índice `idx_receivables_merchant_id_desc`.

---

### Task 3: Núcleo do Domínio Puro (Domain Models, Enums, KeysetPage e Precisão Financeira)

**Files:**
- Create: `src/main/java/com/database/opt/mydbtest/domain/model/CardBrand.java`
- Create: `src/main/java/com/database/opt/mydbtest/domain/model/ReceivableStatus.java`
- Create: `src/main/java/com/database/opt/mydbtest/domain/model/Receivable.java`
- Create: `src/main/java/com/database/opt/mydbtest/domain/model/KeysetPage.java`
- Create: `src/test/java/com/database/opt/mydbtest/domain/model/ReceivableTest.java`

**Interfaces:**
- Consumes: Nenhuma dependência externa ou de framework (Java 25 puro).
- Produces: Entidade pura `Receivable`, enums de domínio e record genérico `KeysetPage<T>(List<T> items, int limit, UUID nextCursor, boolean hasNext)`.

- [ ] **Step 1: Escrever teste de unidade para regras do domínio `ReceivableTest`**
  - Testar criação de recebível com cálculo automático de taxa MDR e valor líquido (`netAmount = grossAmount - feeAmount`) garantindo escala 2 e arredondamento `HALF_UP`.
  - Testar que `createdAt` recebe `Instant.now()` caso não informado.
  - Testar cálculo da data de liquidação (D+30 a partir da transação).
  - Testar validações de invariantes de negócio (valor bruto <= 0 ou taxa negativa lançam `IllegalArgumentException`).
  - Testar fábrica de `KeysetPage.of(items, limit)` extraindo `hasNext` e `nextCursor` a partir de `limit + 1` itens.

- [ ] **Step 2: Executar teste de domínio para confirmar falha inicial**
  - Executar: `.\gradlew.bat test --tests com.database.opt.mydbtest.domain.model.ReceivableTest`
  - Esperado: FAIL (classes ainda não criadas).

- [ ] **Step 3: Implementar `CardBrand`, `ReceivableStatus` e `KeysetPage`**
  - `CardBrand`: `VISA`, `MASTERCARD`, `ELO`, `AMEX`, `HIPERCARD`.
  - `ReceivableStatus`: `PENDING`, `SETTLED`, `CANCELLED`.
  - `KeysetPage<T>(List<T> items, int limit, UUID nextCursor, boolean hasNext)` com método de fábrica `of(List<T> itemsWithExtra, int limit, Function<T, UUID> idExtractor)`.

- [ ] **Step 4: Implementar entidade de domínio puro `Receivable`**
  - Classe Java pura sem anotações de persistência ou Spring.
  - Atributos imutáveis: `id`, `merchantId`, `cardBrand`, `grossAmount`, `feeRate`, `feeAmount`, `netAmount`, `installments`, `installmentNumber`, `status`, `transactionDate`, `settlementDate`, `createdAt`.
  - Construtor/fábrica calculando:
    - `feeAmount = grossAmount.multiply(feeRate).setScale(2, RoundingMode.HALF_UP)`
    - `netAmount = grossAmount.subtract(feeAmount)`
    - `createdAt = (createdAt != null) ? createdAt : Instant.now()`
    - `settlementDate = transactionDate.atZone(ZoneOffset.UTC).toLocalDate().plusDays(30)`

- [ ] **Step 5: Executar teste de domínio para confirmar sucesso**
  - Executar: `.\gradlew.bat test --tests com.database.opt.mydbtest.domain.model.ReceivableTest`
  - Esperado: PASS.

---

### Task 4: Portas de Entrada/Saída e Serviço da Aplicação com Keyset Pagination

**Files:**
- Create: `src/main/java/com/database/opt/mydbtest/domain/ports/in/CreateReceivableCommand.java`
- Create: `src/main/java/com/database/opt/mydbtest/domain/ports/in/CreateReceivableUseCase.java`
- Create: `src/main/java/com/database/opt/mydbtest/domain/ports/in/CreateReceivableBatchUseCase.java`
- Create: `src/main/java/com/database/opt/mydbtest/domain/ports/in/FindReceivableByIdUseCase.java`
- Create: `src/main/java/com/database/opt/mydbtest/domain/ports/in/ListReceivablesKeysetUseCase.java`
- Create: `src/main/java/com/database/opt/mydbtest/domain/ports/out/ReceivableRepositoryPort.java`
- Create: `src/main/java/com/database/opt/mydbtest/application/service/ReceivableApplicationService.java`
- Create: `src/test/java/com/database/opt/mydbtest/application/service/ReceivableApplicationServiceTest.java`

**Interfaces:**
- Consumes: `domain.model.Receivable` e `domain.model.KeysetPage`.
- Produces: Contratos de Use Cases (Driving Ports), Contrato de Repositório (Driven Port) e implementação do serviço de aplicação sem acoplamento a Spring.

- [ ] **Step 1: Definir Portas de Entrada (Driving Ports) e Comandos**
  - Record `CreateReceivableCommand`: `(String merchantId, CardBrand cardBrand, BigDecimal grossAmount, BigDecimal feeRate, Integer installments, Integer installmentNumber, Instant transactionDate)`.
  - Interface `CreateReceivableUseCase`: `Mono<Receivable> create(CreateReceivableCommand command);`
  - Interface `CreateReceivableBatchUseCase`: `Flux<Receivable> createBatch(List<CreateReceivableCommand> commands);`
  - Interface `FindReceivableByIdUseCase`: `Mono<Receivable> findById(UUID id);`
  - Interface `ListReceivablesKeysetUseCase`: `Mono<KeysetPage<Receivable>> listKeyset(String merchantId, UUID cursor, int limit);`

- [ ] **Step 2: Definir Porta de Saída (Driven Port)**
  - Interface `ReceivableRepositoryPort`:
    ```java
    public interface ReceivableRepositoryPort {
        Mono<Receivable> save(Receivable receivable);
        Flux<Receivable> saveAll(List<Receivable> receivables);
        Mono<Receivable> findById(UUID id);
        Flux<Receivable> findKeyset(String merchantId, UUID cursor, int fetchLimit);
    }
    ```

- [ ] **Step 3: Escrever teste de unidade do serviço da aplicação com mock do repositório**
  - Criar `ReceivableApplicationServiceTest` com Mockito simulando `ReceivableRepositoryPort`.
  - Testar `listKeyset`: verificar que o serviço chama `repository.findKeyset(merchantId, cursor, limit + 1)` e monta o `KeysetPage` com `hasNext = true` e o `nextCursor` correto quando há mais itens que `limit`.

- [ ] **Step 4: Implementar `ReceivableApplicationService`**
  - Implementar os casos de uso sem nenhuma anotação de Spring.
  - Em `listKeyset(merchantId, cursor, limit)`:
    - Buscar `limit + 1` itens via `repository.findKeyset(merchantId, cursor, limit + 1)`.
    - Coletar a lista e delegar para `KeysetPage.of(items, limit, Receivable::getId)`.

- [ ] **Step 5: Executar testes de unidade do serviço**
  - Executar: `.\gradlew.bat test --tests com.database.opt.mydbtest.application.service.ReceivableApplicationServiceTest`
  - Esperado: PASS.

---

### Task 5: Adaptador de Saída de Persistência R2DBC (Keyset Seek Query no PostgreSQL)

**Files:**
- Create: `src/main/java/com/database/opt/mydbtest/adapters/outbound/persistence/entity/ReceivableEntity.java`
- Create: `src/main/java/com/database/opt/mydbtest/adapters/outbound/persistence/mapper/ReceivablePersistenceMapper.java`
- Create: `src/main/java/com/database/opt/mydbtest/adapters/outbound/persistence/repository/SpringDataReceivableRepository.java`
- Create: `src/main/java/com/database/opt/mydbtest/adapters/outbound/persistence/adapter/ReceivablePersistenceAdapter.java`
- Create: `src/test/java/com/database/opt/mydbtest/adapters/outbound/persistence/ReceivablePersistenceAdapterTest.java`

**Interfaces:**
- Consumes: Tabela `receivables` via R2DBC e `ReceivableRepositoryPort`.
- Produces: Implementação Spring Data R2DBC com queries nativas de seek segregadas para garantir uso de índice B-tree sem problemas de parâmetro nulo.

- [ ] **Step 1: Criar `ReceivableEntity` implementando `Persistable<UUID>`**
  - Anotação `@Table("receivables")`.
  - Campos: `@Id private UUID id`, `merchantId`, `cardBrand`, `grossAmount`, `feeRate`, `feeAmount`, `netAmount`, `installments`, `installmentNumber`, `status`, `transactionDate`, `settlementDate`, `createdAt`.
  - Campo `@Transient private boolean isNew = false;`.
  - Método `isNew()` retornando `id == null || isNew`.

- [ ] **Step 2: Criar `SpringDataReceivableRepository` com Queries Segregadas de Seek**
  - Interface estendendo `ReactiveCrudRepository<ReceivableEntity, UUID>`.
  - Métodos específicos para garantir que nenhuma cláusula `OR :param IS NULL` prejudique o índice B-tree:
    ```java
    // Primeira página com lojista
    @Query("SELECT * FROM receivables WHERE merchant_id = :merchantId ORDER BY id DESC LIMIT :limit")
    Flux<ReceivableEntity> findFirstPageByMerchant(String merchantId, int limit);

    // Páginas subsequentes com lojista e cursor
    @Query("SELECT * FROM receivables WHERE merchant_id = :merchantId AND id < :cursor ORDER BY id DESC LIMIT :limit")
    Flux<ReceivableEntity> findNextPageByMerchant(String merchantId, UUID cursor, int limit);

    // Primeira página global
    @Query("SELECT * FROM receivables ORDER BY id DESC LIMIT :limit")
    Flux<ReceivableEntity> findFirstPageGlobal(int limit);

    // Páginas subsequentes globais com cursor
    @Query("SELECT * FROM receivables WHERE id < :cursor ORDER BY id DESC LIMIT :limit")
    Flux<ReceivableEntity> findNextPageGlobal(UUID cursor, int limit);
    ```

- [ ] **Step 3: Criar `ReceivablePersistenceMapper`**
  - `toEntity(Receivable domain)`: mapeia campos, garantindo que `createdAt` esteja preenchido e definindo `isNew = (domain.getId() == null)`.
  - `toDomain(ReceivableEntity entity)`: mapeia campos persistidos com o `id` (UUIDv7) gerado pelo banco.

- [ ] **Step 4: Implementar `ReceivablePersistenceAdapter`**
  - Classe `@Component` implementando `ReceivableRepositoryPort`.
  - Em `findKeyset(merchantId, cursor, fetchLimit)`:
    - Se `merchantId != null && !merchantId.isBlank()`:
      - Se `cursor == null` $\rightarrow$ chama `findFirstPageByMerchant`.
      - Se `cursor != null` $\rightarrow$ chama `findNextPageByMerchant`.
    - Se `merchantId == null || merchantId.isBlank()`:
      - Se `cursor == null` $\rightarrow$ chama `findFirstPageGlobal`.
      - Se `cursor != null` $\rightarrow$ chama `findNextPageGlobal`.
    - Converte o `Flux<ReceivableEntity>` para `Flux<Receivable>`.

- [ ] **Step 5: Escrever teste de integração do adaptador de persistência com PostgreSQL real**
  - Criar `ReceivablePersistenceAdapterTest` com `@SpringBootTest`.
  - Salvar recebíveis com `id = null`, validando que retornam com `id.version() == 7`.
  - Validar consulta de keyset com e sem cursor, confirmando que a ordenação e os limites funcionam perfeitamente.

- [ ] **Step 6: Executar teste de persistência**
  - Executar: `.\gradlew.bat test --tests com.database.opt.mydbtest.adapters.outbound.persistence.ReceivablePersistenceAdapterTest`
  - Esperado: PASS.

---

### Task 6: Adaptador de Entrada Web HTTP, GlobalExceptionHandler e Beans de Configuração

**Files:**
- Create: `src/main/java/com/database/opt/mydbtest/adapters/inbound/web/dto/CreateReceivableRequest.java`
- Create: `src/main/java/com/database/opt/mydbtest/adapters/inbound/web/dto/ReceivableResponse.java`
- Create: `src/main/java/com/database/opt/mydbtest/adapters/inbound/web/dto/KeysetPageResponse.java`
- Create: `src/main/java/com/database/opt/mydbtest/adapters/inbound/web/dto/ErrorResponse.java`
- Create: `src/main/java/com/database/opt/mydbtest/adapters/inbound/web/GlobalExceptionHandler.java`
- Create: `src/main/java/com/database/opt/mydbtest/adapters/inbound/web/mapper/ReceivableWebMapper.java`
- Create: `src/main/java/com/database/opt/mydbtest/adapters/inbound/web/ReceivableController.java`
- Create: `src/main/java/com/database/opt/mydbtest/infrastructure/config/BeanConfiguration.java`
- Create: `src/test/java/com/database/opt/mydbtest/adapters/inbound/web/ReceivableControllerTest.java`

**Interfaces:**
- Consumes: Portas de entrada (`CreateReceivableUseCase`, `CreateReceivableBatchUseCase`, `FindReceivableByIdUseCase`, `ListReceivablesKeysetUseCase`).
- Produces: API REST reativa em `/api/v1/receivables` com tratamento global de erros HTTP 400/404/500.

- [ ] **Step 1: Criar DTOs com Jakarta Bean Validation e ErrorResponse**
  - `CreateReceivableRequest`: validação de campos obrigatórios e valores positivos.
  - `ReceivableResponse`: record de representação de um recebível.
  - `KeysetPageResponse<T>(List<T> items, int limit, UUID nextCursor, boolean hasNext)`.
  - `ErrorResponse(Instant timestamp, int status, String error, String message, List<String> details)`.

- [ ] **Step 2: Implementar `GlobalExceptionHandler`**
  - Anotação `@RestControllerAdvice`.
  - Trata `WebExchangeBindException` (HTTP 400 Bad Request) extraindo cada campo inválido.
  - Trata `IllegalArgumentException` (HTTP 400 Bad Request).
  - Trata `ResponseStatusException` (propaga status apropriado, ex.: 404).

- [ ] **Step 3: Criar `BeanConfiguration` na camada de infraestrutura**
  - Declarar bean do `ReceivableApplicationService` injetando `ReceivableRepositoryPort`.

- [ ] **Step 4: Implementar `ReceivableController` e `ReceivableWebMapper`**
  - `POST /api/v1/receivables` -> 201 Created com header `Location`.
  - `POST /api/v1/receivables/batch` -> 201 Created com `Flux<ReceivableResponse>`.
  - `GET /api/v1/receivables/{id}` -> 200 OK / 404 Not Found.
  - `GET /api/v1/receivables`:
    - Parâmetros: `@RequestParam(required = false) String merchantId`, `@RequestParam(required = false) UUID cursor`, `@RequestParam(defaultValue = "20") int limit`.
    - Invoca `ListReceivablesKeysetUseCase.listKeyset(merchantId, cursor, limit)`.
    - Retorna `Mono<KeysetPageResponse<ReceivableResponse>>`.

- [ ] **Step 5: Escrever testes de API com `WebTestClient`**
  - Testar `POST /api/v1/receivables` válido (HTTP 201).
  - Testar `POST /api/v1/receivables` com valor negativo (HTTP 400 com `ErrorResponse` detalhando a falha de validação).
  - Testar `GET /api/v1/receivables` verificando contrato com `items`, `nextCursor` e `hasNext`.

- [ ] **Step 6: Executar testes do controller web**
  - Executar: `.\gradlew.bat test --tests com.database.opt.mydbtest.adapters.inbound.web.ReceivableControllerTest`
  - Esperado: PASS.

---

### Task 7: Teste End-to-End de Ordenação Temporal do UUIDv7 e Paginação por Cursor

**Files:**
- Create: `src/test/java/com/database/opt/mydbtest/UuidV7KeysetPaginationIntegrationTest.java`

**Interfaces:**
- Consumes: Aplicação reativa completa com PostgreSQL 18.6 real via container.
- Produces: Comprovação automatizada da versão 7, da ordenação temporal monotônica dos UUIDs e da navegação contínua por cursor sem dados duplicados nem omitidos.

- [ ] **Step 1: Escrever teste de integração de paginação por cursor**
  - Criar `UuidV7KeysetPaginationIntegrationTest` com `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)`.
  - Cadastrar 15 recebíveis via `POST /api/v1/receivables/batch` com pequenos atrasos para garantir timestamps progressivos no UUIDv7.
  - Fazer requisição da Página 1: `GET /api/v1/receivables?limit=5`.
    - Validar que retorna 5 itens com `id.version() == 7`.
    - Validar que `hasNext == true` e `nextCursor != null`.
    - Validar que os itens estão em ordem decrescente de UUIDv7.
  - Fazer requisição da Página 2: `GET /api/v1/receivables?cursor={nextCursor}&limit=5`.
    - Validar que retorna 5 novos itens, sem duplicatas em relação à Página 1.
    - Validar que todos os IDs da Página 2 são estritamente menores que o cursor usado.
  - Fazer requisição da Página 3: `GET /api/v1/receivables?cursor={nextCursor2}&limit=5`.
    - Validar que retorna os últimos 5 itens.
    - Validar que `hasNext == false` e `nextCursor == null`.

- [ ] **Step 2: Executar teste de validação de UUID v7 e Keyset Pagination**
  - Executar: `.\gradlew.bat test --tests com.database.opt.mydbtest.UuidV7KeysetPaginationIntegrationTest`
  - Esperado: PASS com evidências da paginação contínua nos logs.

- [ ] **Step 3: Executar a suíte de testes completa do projeto**
  - Executar: `.\gradlew.bat test`
  - Esperado: BUILD SUCCESSFUL (100% dos testes passando).
