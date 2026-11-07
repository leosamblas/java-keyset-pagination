# Walkthrough: Integração do Swagger UI / OpenAPI 3.0 no Spring WebFlux

A documentação interativa com **Swagger UI** e especificação **OpenAPI 3.0** foi integrada com sucesso ao projeto reativo `mydbtest` (Spring Boot 4.1.2, Spring Framework 7, Spring WebFlux, Project Reactor e PostgreSQL 18).

---

## 1. Visão Geral da Solução

- **Interface Web Interativa:** Disponível em `http://localhost:8080/swagger-ui.html` (com redirecionamento para o index do Swagger UI).
- **Especificação OpenAPI em JSON:** Disponível em `http://localhost:8080/v3/api-docs`.
- **Arquitetura Hexagonal Preservada:** Nenhuma anotação de documentação ou dependência de framework poluiu as camadas `domain` e `application`. Todo o OpenAPI 3.0 foi isolado em:
  - `infrastructure.config.OpenApiConfig`
  - `adapters.inbound.web.ReceivableController`
  - `adapters.inbound.web.dto.*`
- **Compatibilidade Spring Framework 7:** O SpringDoc WebFlux 2.8.6 internamente utiliza `UriComponentsBuilder.fromHttpRequest(HttpRequest)`, que foi descontinuado e removido no Spring Framework 7. Foi criado um bridge local em `SwaggerWelcomeCommon.java` utilizando `UriComponentsBuilder.fromUri(request.getURI())`, assegurando inicialização estável sem erros em tempo de execução.

---

## 2. Alterações Realizadas

| Componente | Arquivo | Descrição |
| :--- | :--- | :--- |
| **Dependência** | `build.gradle` | Adicionado `org.springdoc:springdoc-openapi-starter-webflux-ui:2.8.6` |
| **Configuração** | `src/main/resources/application.properties` | Caminhos `/swagger-ui.html` e `/v3/api-docs` |
| **Metadados OpenAPI** | `OpenApiConfig.java` | Bean `OpenAPI` com título, descrição, versão e contato |
| **Bridge Spring 7** | `SwaggerWelcomeCommon.java` | Patch para resolver remoção de método no Spring Framework 7 |
| **Documentação Controller** | `ReceivableController.java` | Anotações `@Tag`, `@Operation`, `@ApiResponses`, `@Parameter` |
| **Documentação DTOs** | `CreateReceivableRequest.java`, `ReceivableResponse.java`, etc. | Anotações `@Schema` com descrições, exemplos e restrições |
| **Testes Automatizados** | `OpenApiIntegrationTest.java` | Validação automática de `/v3/api-docs`, redirecionamento e Swagger UI bundle |

---

## 3. Verificação Automatizada

A suíte completa de testes (7 classes de teste cobrindo todas as camadas) foi executada e validada:

```powershell
.\gradlew.bat test --rerun-tasks
```

### Resultados dos Testes

- `ReceivableTest` (Regras de Domínio): **PASSED**
- `ReceivableApplicationServiceTest` (Portas & Casos de Uso): **PASSED**
- `ReceivablePersistenceAdapterTest` (Queries R2DBC no PostgreSQL 18): **PASSED**
- `ReceivableControllerTest` (API Web reativa & Validações): **PASSED**
- `ReceivableKeysetPaginationE2ETest` (UUIDv7 e Paginação por Cursor): **PASSED**
- `MydbtestApplicationTests` (Flyway & Context Boot): **PASSED**
- `OpenApiIntegrationTest` (JSON OpenAPI e Swagger UI WebFlux): **PASSED**

**Status final:** `BUILD SUCCESSFUL in 13s` — 0 falhas, 0 erros.

---

## 4. Guia de Teste Manual via Navegador

Para interagir com o Swagger UI no navegador:

### Passo 1: Iniciar a Aplicação
Certifique-se de que o contêiner `postgresql-service` está em execução (`docker ps`) e inicie a aplicação Spring Boot:

```powershell
.\gradlew.bat bootRun
```

### Passo 2: Acessar no Navegador
Abra o navegador no endereço:
👉 **http://localhost:8080/swagger-ui.html**

### Passo 3: Executar Operações Interativas ("Try it out")

1. **Cadastrar Recebível (`POST /api/v1/receivables`):**
   - Clique em **POST /api/v1/receivables** -> **Try it out**.
   - Envie o payload JSON de exemplo:
     ```json
     {
       "merchantId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
       "amount": 250.75,
       "cardBrand": "MASTERCARD"
     }
     ```
   - Clique em **Execute**.
   - Verifique o retorno **201 Created** com o `id` gerado no padrão UUIDv7.

2. **Listar com Paginação Keyset Seek (`GET /api/v1/receivables`):**
   - Clique em **GET /api/v1/receivables** -> **Try it out**.
   - Defina `merchantId` como `9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d` e `limit` como `10`.
   - Clique em **Execute**.
   - Observe a lista de itens e o campo `nextCursor` para a próxima página de busca.

3. **Consultar por ID (`GET /api/v1/receivables/{id}`):**
   - Cole o UUIDv7 retornado na criação e clique em **Execute**.
   - Verifique a resposta **200 OK**.
