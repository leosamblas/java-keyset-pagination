# Walkthrough: Módulo de Seed de Testes em JavaScript (`js/`)

O módulo de geração e inserção de dados mockados em massa (*seed*) foi implementado com sucesso na pasta `js/` do projeto `mydbtest`, utilizando **Node.js 24**, **ECMAScript Modules (`type: "module"`)** e a biblioteca **`@faker-js/faker`**.

---

## 1. Visão Geral da Solução

O módulo fornece geração sintética de transações financeiras de recebíveis de cartão, com dois modos complementares de operação:

1. **Modo HTTP (`node seed.js` ou `npm run seed`):**
   - Dispara requisições `POST /api/v1/receivables` contra a API Spring WebFlux em execução.
   - Utiliza um pool de concorrência com **`fetch` nativo** do Node.js (`--concurrency=10`).
   - Testa a pipeline reativa completa: Jakarta Bean Validation, DTOs, cálculos de taxas e datas de liquidação na camada de domínio, e persistência via R2DBC no PostgreSQL 18.
   - Utiliza um **pool fixo de lojistas (*merchants*)**, permitindo testar realisticamente a **paginação por cursor (keyset pagination)** no Swagger UI (`http://localhost:8080/swagger-ui.html`).

2. **Modo SQL (`node seed.js --mode=sql` ou `npm run seed:sql`):**
   - Gera um arquivo `.sql` otimizado contendo múltiplos comandos `INSERT INTO receivables`.
   - Utiliza a função nativa **`uuidv7()` do PostgreSQL 18**, gerando identificadores monotonicamente ordenados no tempo para testes de estresse ou cargas massivas (ex: 50.000 registros).

---

## 2. Arquivos Implementados

| Arquivo | Descrição |
| :--- | :--- |
| `js/package.json` | Configuração do projeto Node.js (`type: "module"`), scripts npm e dependência `@faker-js/faker@^10.6.0`. |
| `js/seed.js` | Script executável com parser de CLI, gerador Faker, despachante HTTP reativo concorrente e gerador SQL em lotes. |
| `js/README.md` | Documentação completa com guia de instalação, tabelas de parâmetros e instruções de teste no Swagger UI. |
| `.gitignore` | Configuração para ignorar `node_modules/`, `js/node_modules/` e arquivos temporários `*.sql`. |

---

## 3. Verificação Realizada

### 3.1. Validação de Sintaxe e Ajuda da CLI
- Executado `node --check js/seed.js` -> Sintaxe válida (código 0).
- Executado `node seed.js --help` -> Parâmetros (`--count`, `--concurrency`, `--merchants`, `--api-url`, `--mode`, `--output`) exibidos com clareza.

### 3.2. Teste do Gerador SQL e Validação com PostgreSQL 18
Executado comando de teste:
```powershell
node seed.js --mode=sql --count=20 --output=test-seed.sql
```
O arquivo SQL foi aplicado com sucesso diretamente no contêiner `postgresql-service`:
```powershell
Get-Content js/test-seed.sql | docker exec -i postgresql-service psql -U postgres -d postgres
# Retorno: INSERT 0 20
```

Consulta aos registros inseridos no PostgreSQL 18 confirmou a geração válida e monotonicamente crescente do **UUIDv7**:
```text
                  id                  | merchant_id | card_brand | gross_amount | net_amount | status  
--------------------------------------+-------------+------------+--------------+------------+---------
 01a1b782-d599-738b-801a-246f4437daa0 | merch-001   | HIPERCARD  |      1636.82 |    1592.30 | PENDING
 01a1b782-d599-7426-81e0-83b5c096993d | merch-001   | ELO        |      1914.92 |    1859.20 | PENDING
 01a1b782-d599-74ec-aee2-3da718e6e08e | merch-001   | HIPERCARD  |      2178.03 |    2083.50 | PENDING
 01a1b782-d599-7692-ab8f-6ed071ef46d1 | merch-001   | AMEX       |       706.32 |     681.18 | PENDING
```

### 3.3. Suíte de Testes do Backend Spring
Executado `.\gradlew.bat test` -> **BUILD SUCCESSFUL** (todas as 7 suítes de teste de integração e unidade permaneceram 100% verdes).

---

## 4. Como Executar o Seed

### Opção A: Seed via API HTTP Reativa (Recomendado)

1. Inicie a aplicação Spring Boot:
   ```powershell
   .\gradlew.bat bootRun
   ```

2. Em outro terminal, navegue até a pasta `js/` e execute:
   ```powershell
   cd js
   npm run seed
   ```
   *Ou com parâmetros personalizados:*
   ```powershell
   node seed.js --count=200 --concurrency=15 --merchants=5
   ```

3. Abra o Swagger UI no navegador:
   👉 **http://localhost:8080/swagger-ui.html**
   - No endpoint `GET /api/v1/receivables`, informe `merchantId: merch-001` e `limit: 10`.
   - Execute a chamada e use o `nextCursor` para navegar pelas próximas páginas.

---

### Opção B: Carga Direta em Lote via SQL (Para Grandes Volumes)

1. Gere o arquivo SQL na pasta `js/`:
   ```powershell
   cd js
   npm run seed:sql
   ```

2. Aplique no PostgreSQL rodando no Docker:
   ```powershell
   docker exec -i postgresql-service psql -U postgres -d postgres < seed-receivables.sql
   ```
