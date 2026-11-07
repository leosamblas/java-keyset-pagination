# Módulo de Seed de Testes (`js/`)

Script de geração e inserção de dados mockados realistas para o projeto `mydbtest` utilizando **Node.js 24**, **ECMAScript Modules (`type: "module"`)** e **`@faker-js/faker`**.

O script foi projetado com **geração sob demanda (lazy generation)** e **streaming com backpressure**, garantindo consumo constante e mínimo de memória RAM (**O(concorrência)**, ~25MB), suportando desde dezenas de itens até centenas de milhões de registros sem estouro de pilha (*Out of Memory*).

---

## 1. Pré-Requisitos

- **Node.js**: v20+ ou v24+ (com suporte nativo a `fetch`).
- **PostgreSQL 18** rodando no Docker (`postgresql-service`).
- **Aplicação Spring Boot** em execução (para o modo HTTP).

---

## 2. Instalação

Acesse a pasta `js/` e instale as dependências:

```powershell
cd js
npm install
```

---

## 3. Modos de Uso

### 3.1. Modo HTTP Individual (`POST /api/v1/receivables`)
Dispara requisições individuais contra a aplicação Spring Boot, validando cada recebível de forma isolada.

```powershell
# Execução padrão (100 recebíveis, concorrência 10, 5 lojistas)
npm run seed

# Execução rápida (50 recebíveis)
npm run seed:quick
```

---

### 3.2. Modo HTTP Batch (`POST /api/v1/receivables/batch`) — Alta Vazão ⚡
Dispara requisições em lote para o endpoint reativo de batch da API, processando centenas ou milhares de itens por requisição. Reduz o overhead de rede e acelera drasticamente o povoamento do banco.

```powershell
# 5.000 recebíveis em lotes de 250 (10 conexões simultâneas)
npm run seed:batch

# Teste de estresse: 50.000 recebíveis em lotes de 500 (20 conexões simultâneas)
npm run seed:stress

# Customizado via CLI:
node seed.js --batch --count=20000 --batch-size=500 --concurrency=15
```

---

### 3.3. Modo SQL (Carga Direta em Arquivo para o PostgreSQL 18)
Gera um arquivo SQL com múltiplos comandos `INSERT INTO receivables` formatados e prontos para execução em lote via `psql`, utilizando a função nativa `uuidv7()` do PostgreSQL 18.

```powershell
# Gera o arquivo seed-receivables.sql com 1.000 registros
npm run seed:sql

# Ou parametrizado:
node seed.js --mode=sql --count=5000 --output=carga-5k.sql
```

Para aplicar o arquivo gerado diretamente no banco dentro do contêiner Docker:
```powershell
docker exec -i postgresql-service psql -U postgres -d postgres < seed-receivables.sql
```

---

## 4. Parâmetros da CLI (`seed.js`)

| Parâmetro | Padrão | Descrição |
| :--- | :--- | :--- |
| `--count=N` | `100` | Quantidade total de registros de recebíveis a gerar. |
| `--concurrency=N` | `10` | Quantidade de conexões HTTP paralelas simultâneas. |
| `--batch` | `false` | Ativa envio em lote via `POST /api/v1/receivables/batch`. |
| `--batch-size=N` | `200` | Quantidade de itens enviados por requisição no modo batch. |
| `--api-url=URL` | `http://localhost:8080` | URL base da API Spring WebFlux. |
| `--merchants=N` | `5` | Quantidade de lojistas no pool para possibilitar teste de paginação keyset. |
| `--mode=MODE` | `http` | Modo de seed: `http`, `batch` ou `sql` (gera arquivo `.sql`). |
| `--output=FILE` | `seed-receivables.sql` | Nome do arquivo SQL de saída no modo `sql`. |
| `--help`, `-h` | — | Exibe menu de ajuda com todos os parâmetros. |

---

## 5. Testando no Swagger UI

Após executar o seed:
1. Abra no navegador: **[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)**
2. Expanda o endpoint `GET /api/v1/receivables`.
3. Clique em **Try it out**.
4. Informe o `merchantId` (ex: `merch-001`) e `limit` (ex: `10`).
5. Clique em **Execute** e copie o valor de `nextCursor` para testar a paginação por cursor (keyset seek).

