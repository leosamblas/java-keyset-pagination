# Relatório Comparativo de Performance (250k Recebíveis)

Comparativo entre o endpoint individual e o endpoint em lote da **Card Receivables Reactive Engine**.

## 1. Parâmetros de Execução
- **Volume:** 250.000 registros por teste
- **Concorrência:** 20 conexões simultâneas
- **Tamanho do Lote (Batch):** 250 itens por requisição HTTP
- **Banco de Dados:** PostgreSQL 18 com UUIDv7 nativo
- **Stack:** Spring Boot 4.1.2 + Spring WebFlux + Project Reactor + R2DBC Pool

---

## 2. Tabela Comparativa de Resultados

| Métrica | Endpoint Individual (`POST /receivables`) | Endpoint Batch (`POST /receivables/batch`) | Variação / Ganho |
| :--- | :--- | :--- | :--- |
| **Endpoint** | `http://localhost:8080/api/v1/receivables` | `http://localhost:8080/api/v1/receivables/batch` | — |
| **Itens Processados** | 250.000 | 250.000 | Igual |
| **Requisições HTTP** | 250.000 | 1.000 | **-99.60%** |
| **Tempo Total** | **77.4s** | **37.62s** | **39.78s mais rápido** |
| **Vazão Efetiva** | **3.230 itens/s** | **6.645,4 itens/s** | **2.06x mais rápido** |
| **Taxa de Sucesso** | 250.000 (100%) | 250.000 (100%) | 100% íntegro |

---

## 3. Conclusão Técnica
1. **Redução de Overhead de Rede:** O agrupamento de 250 itens por payload eliminou 249.000 round-trips de socket TCP e handshake HTTP.
2. **Eficiência no R2DBC:** O método `repository.saveAll()` permitiu ao driver R2DBC e ao PostgreSQL 18 executarem inserções em lote otimizadas com `uuidv7()` nativo, minimizando o custo de transação e locks de I/O.
