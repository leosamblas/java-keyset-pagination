#!/usr/bin/env node
/**
 * seed.js — Gerador de Dados Mockados de Teste para o projeto mydbtest
 * 
 * Gera transações de recebíveis com @faker-js/faker e insere via API HTTP reativa
 * (Spring WebFlux) ou gera script SQL para carga em lote no PostgreSQL 18.
 */

import { faker } from '@faker-js/faker';
import { writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const CARD_BRANDS = ['VISA', 'MASTERCARD', 'ELO', 'AMEX', 'HIPERCARD'];

// ============================================================================
// CLI Arguments Parsing
// ============================================================================
function parseArgs() {
  const args = process.argv.slice(2);
  const options = {
    count: 100,
    concurrency: 10,
    apiUrl: 'http://localhost:8080',
    merchants: 5,
    mode: 'http',
    output: 'seed-receivables.sql',
    help: false,
  };

  for (const arg of args) {
    if (arg === '--help' || arg === '-h') {
      options.help = true;
    } else if (arg.startsWith('--count=')) {
      options.count = Math.max(1, parseInt(arg.split('=')[1], 10) || 100);
    } else if (arg.startsWith('--concurrency=')) {
      options.concurrency = Math.max(1, parseInt(arg.split('=')[1], 10) || 10);
    } else if (arg.startsWith('--api-url=')) {
      options.apiUrl = arg.split('=')[1].replace(/\/+$/, '');
    } else if (arg.startsWith('--merchants=')) {
      options.merchants = Math.max(1, parseInt(arg.split('=')[1], 10) || 5);
    } else if (arg.startsWith('--mode=')) {
      const mode = arg.split('=')[1].toLowerCase();
      options.mode = mode === 'sql' ? 'sql' : 'http';
    } else if (arg.startsWith('--output=')) {
      options.output = arg.split('=')[1];
    }
  }

  return options;
}

function showHelp() {
  console.log(`
Uso: node seed.js [opções]

Opções:
  --count=N           Quantidade de recebíveis a gerar (padrão: 100)
  --concurrency=N     Número de requisições HTTP paralelas em modo http (padrão: 10)
  --api-url=URL       URL base da API Spring WebFlux (padrão: http://localhost:8080)
  --merchants=N       Tamanho do pool de lojistas para teste de paginação (padrão: 5)
  --mode=http|sql     Modo de execução: 'http' (via REST API) ou 'sql' (gera arquivo .sql) (padrão: http)
  --output=FILE       Nome do arquivo no modo SQL (padrão: seed-receivables.sql)
  --help, -h          Exibe esta ajuda

Exemplos:
  node seed.js                                    # 100 itens via HTTP contra localhost:8080
  node seed.js --count=500 --concurrency=20       # 500 itens com concorrência 20
  node seed.js --mode=sql --count=1000            # Gera arquivo SQL com 1.000 INSERTs
`);
}

// ============================================================================
// Data Generation Helpers
// ============================================================================
function generateMerchantPool(size) {
  return Array.from({ length: size }, (_, i) => `merch-${String(i + 1).padStart(3, '0')}`);
}

function generateMockReceivable(merchants) {
  const installments = faker.number.int({ min: 1, max: 12 });
  const installmentNumber = faker.number.int({ min: 1, max: installments });
  const grossAmount = parseFloat(faker.finance.amount({ min: 10.0, max: 2500.0, dec: 2 }));
  const feeRate = parseFloat((faker.number.float({ min: 0.015, max: 0.045, fractionDigits: 4 })).toFixed(4));

  return {
    merchantId: faker.helpers.arrayElement(merchants),
    cardBrand: faker.helpers.arrayElement(CARD_BRANDS),
    grossAmount,
    feeRate,
    installments,
    installmentNumber,
    transactionDate: faker.date.recent({ days: 60 }).toISOString(),
  };
}

// ============================================================================
// HTTP Seeder (Native fetch with Concurrency Worker Pool)
// ============================================================================
async function runHttpSeeder(options, merchants) {
  const endpoint = `${options.apiUrl}/api/v1/receivables`;
  console.log(`\n============================================================`);
  console.log(`[SEED] Modo HTTP: Iniciando envio de ${options.count} recebíveis`);
  console.log(`[SEED] Endpoint:    ${endpoint}`);
  console.log(`[SEED] Concorrência: ${options.concurrency} requisições simultâneas`);
  console.log(`[SEED] Lojistas:     ${merchants.join(', ')}`);
  console.log(`============================================================\n`);

  let completed = 0;
  let succeeded = 0;
  let failed = 0;
  const errors = [];
  const startTime = Date.now();

  const items = Array.from({ length: options.count }, () => generateMockReceivable(merchants));
  let currentIndex = 0;

  async function worker(workerId) {
    while (currentIndex < items.length) {
      const idx = currentIndex++;
      const payload = items[idx];

      try {
        const response = await fetch(endpoint, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload),
        });

        completed++;
        if (response.status === 201) {
          succeeded++;
        } else {
          failed++;
          const body = await response.text();
          if (errors.length < 5) {
            errors.push(`Status ${response.status}: ${body.substring(0, 100)}`);
          }
        }
      } catch (err) {
        completed++;
        failed++;
        if (errors.length < 5) {
          errors.push(err.message || 'Connection error');
        }
      }

      if (completed % Math.max(1, Math.floor(options.count / 10)) === 0 || completed === options.count) {
        const pct = Math.round((completed / options.count) * 100);
        process.stdout.write(`\r[SEED] Progresso: ${completed}/${options.count} (${pct}%) — Sucesso: ${succeeded}, Falhas: ${failed}`);
      }
    }
  }

  const workers = Array.from({ length: options.concurrency }, (_, i) => worker(i));
  await Promise.all(workers);

  const durationSec = ((Date.now() - startTime) / 1000).toFixed(2);
  const rps = (options.count / Math.max(0.001, parseFloat(durationSec))).toFixed(1);

  console.log(`\n\n============================================================`);
  console.log(`[SEED] Concluído em ${durationSec}s (${rps} req/s)`);
  console.log(`[SEED] Total:     ${options.count}`);
  console.log(`[SEED] Sucessos:  ${succeeded} (HTTP 201 Created)`);
  console.log(`[SEED] Falhas:    ${failed}`);
  if (failed > 0) {
    console.log(`[SEED] Primeiros erros encontrados:`);
    errors.forEach((err) => console.log(`   - ${err}`));
    if (errors.some(e => e.includes('ECONNREFUSED') || e.includes('fetch failed'))) {
      console.log(`\n[DICA] Certifique-se de que a aplicação Spring Boot está rodando:`);
      console.log(`       .\\gradlew.bat bootRun`);
    }
  }
  console.log(`============================================================`);

  if (succeeded > 0) {
    printSwaggerGuide(options.apiUrl, merchants[0]);
  }
}

// ============================================================================
// SQL File Seeder (Direct bulk INSERT generation with PostgreSQL 18 uuidv7())
// ============================================================================
function runSqlSeeder(options, merchants) {
  const filePath = resolve(process.cwd(), options.output);
  console.log(`\n============================================================`);
  console.log(`[SEED] Modo SQL: Gerando ${options.count} recebíveis em arquivo`);
  console.log(`[SEED] Arquivo:  ${filePath}`);
  console.log(`[SEED] Lojistas: ${merchants.join(', ')}`);
  console.log(`============================================================\n`);

  const startTime = Date.now();
  const lines = [
    `-- ============================================================================`,
    `-- Seed de Recebíveis gerado por js/seed.js em ${new Date().toISOString()}`,
    `-- PostgreSQL 18 nativo com uuidv7() e Keyset Pagination`,
    `-- Total de registros: ${options.count}`,
    `-- ============================================================================`,
    ``,
  ];

  const BATCH_SIZE = 100;
  for (let i = 0; i < options.count; i += BATCH_SIZE) {
    const batchCount = Math.min(BATCH_SIZE, options.count - i);
    const valueTuples = [];

    for (let j = 0; j < batchCount; j++) {
      const item = generateMockReceivable(merchants);
      const gross = item.grossAmount;
      const rate = item.feeRate;
      const feeAmount = Math.round(gross * rate * 100) / 100;
      const netAmount = Math.round((gross - feeAmount) * 100) / 100;

      const txDate = new Date(item.transactionDate);
      const settlementDate = new Date(txDate.getTime() + 30 * 24 * 60 * 60 * 1000)
        .toISOString()
        .slice(0, 10);
      const status = settlementDate < new Date().toISOString().slice(0, 10) ? 'SETTLED' : 'PENDING';

      valueTuples.push(
        `  (uuidv7(), '${item.merchantId}', '${item.cardBrand}', ${gross.toFixed(2)}, ${rate.toFixed(4)}, ` +
        `${feeAmount.toFixed(2)}, ${netAmount.toFixed(2)}, ${item.installments}, ${item.installmentNumber}, ` +
        `'${status}', '${txDate.toISOString()}', '${settlementDate}', clock_timestamp())`
      );
    }

    lines.push(
      `INSERT INTO receivables (id, merchant_id, card_brand, gross_amount, fee_rate, fee_amount, net_amount, installments, installment_number, status, transaction_date, settlement_date, created_at) VALUES\n` +
      valueTuples.join(',\n') +
      `;\n`
    );
  }

  writeFileSync(filePath, lines.join('\n'), 'utf8');
  const durationSec = ((Date.now() - startTime) / 1000).toFixed(2);

  console.log(`[SEED] Arquivo SQL gerado com sucesso: ${filePath}`);
  console.log(`[SEED] Tempo decorrido: ${durationSec}s`);
  console.log(`\nPara aplicar diretamente no PostgreSQL via Docker:`);
  console.log(`  docker exec -i postgresql-service psql -U postgres -d postgres < ${options.output}`);
  console.log(`============================================================`);
  printSwaggerGuide('http://localhost:8080', merchants[0]);
}

// ============================================================================
// Post-Seed Instructions for Swagger UI and Keyset Pagination
// ============================================================================
function printSwaggerGuide(apiUrl, sampleMerchant) {
  console.log(`\nPronto para testar no Swagger UI!`);
  console.log(`1. Abra no navegador: ${apiUrl}/swagger-ui.html`);
  console.log(`2. Vá em: GET /api/v1/receivables`);
  console.log(`3. Preencha os parâmetros:`);
  console.log(`     merchantId: ${sampleMerchant}`);
  console.log(`     limit:      10`);
  console.log(`4. Clique em "Execute" e use o campo "nextCursor" retornado para paginar!`);
  console.log(`\nOu teste via cURL:`);
  console.log(`  curl -s "${apiUrl}/api/v1/receivables?merchantId=${sampleMerchant}&limit=10" | jq .\n`);
}

// ============================================================================
// Main Entrypoint
// ============================================================================
async function main() {
  const options = parseArgs();
  if (options.help) {
    showHelp();
    return;
  }

  const merchants = generateMerchantPool(options.merchants);

  if (options.mode === 'sql') {
    runSqlSeeder(options, merchants);
  } else {
    await runHttpSeeder(options, merchants);
  }
}

main().catch((err) => {
  console.error('[ERRO FATAL]', err);
  process.exit(1);
});
