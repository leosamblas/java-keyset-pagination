# [Plano de Implementação] Inclusão do Swagger UI (OpenAPI 3) no Spring WebFlux

## Visão Geral e Metas (Goal Description)
Disponibilizar interface visual interativa do **Swagger UI (OpenAPI 3.0)** na aplicação reativa **Spring WebFlux** para permitir a exploração, teste manual e validação das rotas REST de recebíveis diretamente pelo navegador web em `http://localhost:8080/swagger-ui.html`.

A implementação respeita estritamente a **Arquitetura Hexagonal**:
- O núcleo de domínio (`domain.*`) e os serviços de aplicação (`application.*`) permanecem **100% puros** em Java 25, sem nenhuma dependência ou anotação do Swagger/OpenAPI.
- As anotações do Swagger (`@Tag`, `@Operation`, `@ApiResponse`, `@Parameter`, `@Schema`) são aplicadas exclusivamente no Adaptador Web de Entrada (`adapters.inbound.web.ReceivableController`) e nos seus DTOs de transporte (`dto.*`).
- A configuração central do OpenAPI é isolada na camada de infraestrutura (`infrastructure.config.OpenApiConfig`).

---

## User Review Required

> [!IMPORTANT]
> **Compatibilidade Reativa (WebFlux vs MVC):**
> A aplicação roda sobre **Spring WebFlux / Netty** e não sobre Spring MVC / Tomcat. Usamos a biblioteca oficial reativa `org.springdoc:springdoc-openapi-starter-webflux-ui:2.8.6`, que registra manipuladores nativos no WebFlux (`RouterFunction` e WebJars reativos) sem introduzir servlets bloqueantes.

> [!NOTE]
> **Acesso Web Padrão:**
> - **Swagger UI Interativo:** `http://localhost:8080/swagger-ui.html` (com redirecionamento automático para a interface gráfica WebJar).
> - **Especificação OpenAPI JSON:** `http://localhost:8080/v3/api-docs`.

---

## Open Questions

Nenhuma dúvida bloqueante identificada. A biblioteca e as versões já foram validadas contra o ecossistema Spring Boot reativo do projeto.

---

## Proposed Changes

### 1. Dependências do Projeto

#### [MODIFY] `build.gradle`
Adicionar a dependência reativa do SpringDoc OpenAPI Starter WebFlux UI:
```groovy
dependencies {
    // OpenAPI / Swagger UI para Spring WebFlux
    implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.8.6'
    ...
}
```

---

### 2. Configurações da Aplicação

#### [MODIFY] `src/main/resources/application.properties`
Configurar parâmetros do SpringDoc para habilitar Swagger UI com "Try it out" ativo por padrão e caminhos amigáveis:
```properties
# OpenAPI / Swagger UI
springdoc.api-docs.path=/v3/api-docs
springdoc.api-docs.enabled=true
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.swagger-ui.enabled=true
springdoc.swagger-ui.try-it-out-enabled=true
springdoc.swagger-ui.operations-sorter=method
springdoc.swagger-ui.tags-sorter=alpha
```

---

### 3. Camada de Infraestrutura (Configuração OpenAPI)

#### [NEW] `src/main/java/com/database/opt/mydbtest/infrastructure/config/OpenApiConfig.java`
Configurar bean `OpenAPI` com metadados detalhados da aplicação:
- **Título:** "API de Gestão de Recebíveis de Cartão"
- **Descrição:** "API reativa de alto desempenho para cadastro e consulta de recebíveis com UUIDv7 nativo do PostgreSQL 18 e Keyset Pagination (Seek Pagination)."
- **Versão:** "1.0.0"
- **Contrato:** Suporte à RFC 9562 (UUIDv7) e parâmetros de paginação por cursor.

---

### 4. Camada de Adaptadores Web (Anotações OpenAPI)

#### [MODIFY] `src/main/java/com/database/opt/mydbtest/adapters/inbound/web/ReceivableController.java`
Enriquecer o controller com metadados OpenAPI:
- `@Tag(name = "Recebíveis", description = "Operações reativas de cadastro e consulta de recebíveis de cartão com Keyset Pagination e UUIDv7")`
- `POST /api/v1/receivables`: `@Operation`, `@ApiResponse`
- `POST /api/v1/receivables/batch`: `@Operation`, `@ApiResponse`
- `GET /api/v1/receivables/{id}`: `@Operation`, `@Parameter`, `@ApiResponse`
- `GET /api/v1/receivables`: `@Operation`, `@Parameter` (com explicações para `merchantId`, `cursor` e `limit`)

#### [MODIFY] DTOs de Entrada e Saída
Adicionar anotações `@Schema` com descrições e exemplos claros para a UI do Swagger:
- `CreateReceivableRequest.java`
- `ReceivableResponse.java`
- `KeysetPageResponse.java`
- `ErrorResponse.java`

---

### 5. Testes Automatizados

#### [NEW] `src/test/java/com/database/opt/mydbtest/infrastructure/OpenApiIntegrationTest.java`
- `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)`
- Teste 1: `GET /v3/api-docs` retorna status 200 OK com payload JSON válido contendo os caminhos `/api/v1/receivables` e informações da API.
- Teste 2: `GET /swagger-ui.html` responde com status 200 OK ou 303 Redirect para `/webjars/swagger-ui/index.html`.

---

## Verification Plan

### Automated Tests
1. **Compilação e Resolução de Dependências:**
   ```bash
   .\gradlew.bat compileJava compileTestJava
   ```
2. **Executar teste dedicado do Swagger / OpenAPI:**
   ```bash
   .\gradlew.bat test --tests com.database.opt.mydbtest.infrastructure.OpenApiIntegrationTest
   ```
3. **Executar suite de testes completa (regressão zero):**
   ```bash
   .\gradlew.bat test
   ```

### Manual Verification
1. Iniciar a aplicação localmente:
   ```bash
   .\gradlew.bat bootRun
   ```
2. Abrir o navegador em:
   ```
   http://localhost:8080/swagger-ui.html
   ```
3. Testar os fluxos via interface gráfica:
   - **POST `/api/v1/receivables`:** Clicar em "Try it out", enviar o payload de exemplo e verificar o retorno 201 Created com o `id` (UUIDv7 gerado pelo banco).
   - **GET `/api/v1/receivables`:** Executar com `limit=5`, obter a primeira página e copiar o `nextCursor`.
   - **GET `/api/v1/receivables` com cursor:** Colar o `nextCursor` copiado e executar para navegar na página seguinte.

