#!/usr/bin/env node
/**
 * seed.js — Gerador de Dados Mockados de Teste para o projeto mydbtest
 * 
 * Gera transações de recebíveis com @faker-js/faker e insere via API HTTP reativa
 * (Spring WebFlux) ou gera script SQL para carga em lote no PostgreSQL 18.
 * 
 * Suporta volumes arbitrários (inclusive centenas de milhões de registros) com
 * consumo constante de memória O(concorrência) via geração lazy/streaming.
 */

import { faker } from '@faker-js/faker';
import { createWriteStream } from 'node:fs';
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
    batch: false,
    batchSize: 200,
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
      if (mode === 'sql') {
        options.mode = 'sql';
      } else if (mode === 'batch') {
        options.mode = 'http';
        options.batch = true;
      } else {
        options.mode = 'http';
      }
    } else if (arg.startsWith('--output=')) {
      options.output = arg.split('=')[1];
    } else if (arg === '--batch') {
      options.batch = true;
    } else if (arg.startsWith('--batch-size=')) {
      options.batchSize = Math.max(1, parseInt(arg.split('=')[1], 10) || 200);
      options.batch = true;
    }
  }

  return options;
}

function showHelp() {
  console.log(`
Uso: node seed.js [opções]

Opções:
  --count=N           Quantidade de recebíveis a gerar (padrão: 100)
  --concurrency=N     Número de conexões HTTP paralelas em modo http (padrão: 10)
  --batch             Ativa envio em lote via endpoint reativo /api/v1/receivables/batch
  --batch-size=N      Tamanho de cada lote no modo batch (padrão: 200)
  --api-url=URL       URL base da API Spring WebFlux (padrão: http://localhost:8080)
  --merchants=N       Tamanho do pool de lojistas para teste de paginação (padrão: 5)
  --mode=http|batch|sql Modo de execução: 'http', 'batch' ou 'sql' (padrão: http)
  --output=FILE       Nome do arquivo no modo SQL (padrão: seed-receivables.sql)
  --help, -h          Exibe esta ajuda

Exemplos:
  node seed.js                                    # 100 itens individuais via HTTP
  node seed.js --batch --count=5000               # 5.000 itens em lotes de 200 via /batch
  node seed.js --batch --count=50000 --batch-size=500 --concurrency=20 # Carga massiva
  node seed.js --mode=sql --count=1000000         # Gera arquivo SQL com 1 milhão de INSERTs
`);
}

// ============================================================================
// Data Generation Helpers (On-Demand / Zero-Allocation)
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

function formatSeconds(totalSec) {
  if (totalSec < 60) return `${Math.round(totalSec)}s`;
  const minutes = Math.floor(totalSec / 60);
  const seconds = Math.round(totalSec % 60);
  if (minutes < 60) return `${minutes}m ${seconds}s`;
  const hours = Math.floor(minutes / 60);
  const remainingMinutes = minutes % 60;
  return `${hours}h ${remainingMinutes}m`;
}

// ============================================================================
// HTTP Seeder (Lazy Generation, O(concurrency * batchSize) Memory, Smooth Progress)
// ============================================================================
async function runHttpSeeder(options, merchants) {
  const isBatch = Boolean(options.batch);
  const endpoint = isBatch
    ? `${options.apiUrl}/api/v1/receivables/batch`
    : `${options.apiUrl}/api/v1/receivables`;

  console.log(`\n============================================================`);
  console.log(`[SEED] Modo HTTP ${isBatch ? `(Batch: ${options.batchSize} itens/lote)` : '(Individual)'}: Iniciando envio de ${options.count.toLocaleString()} recebíveis`);
  console.log(`[SEED] Endpoint:    ${endpoint}`);
  console.log(`[SEED] Concorrência: ${options.concurrency} conexões simultâneas`);
  console.log(`[SEED] Lojistas:     ${merchants.join(', ')}`);
  console.log(`[SEED] Memória:      O(concorrência * batchSize) — Streaming lazy sob demanda`);
  console.log(`============================================================\n`);

  let completed = 0;
  let succeeded = 0;
  let failed = 0;
  let dispatched = 0;
  let currentIndex = 0;
  let stopRequested = false;
  const errors = [];
  const startTime = Date.now();
  let lastLogTime = 0;

  // Graceful shutdown on Ctrl+C: stop dispatching new items and show summary
  const sigintHandler = () => {
    if (stopRequested) {
      console.log('\n[SEED] Forçando encerramento imediato...');
      process.exit(1);
    }
    stopRequested = true;
    console.log('\n[SEED] Interrupção detectada (Ctrl+C). Finalizando requisições pendentes...');
  };
  process.on('SIGINT', sigintHandler);

  function logProgress(force = false) {
    const now = Date.now();
    if (!force && now - lastLogTime < 500) return;
    lastLogTime = now;

    const elapsedSec = (now - startTime) / 1000;
    const currentRps = (completed / Math.max(0.001, elapsedSec)).toFixed(0);
    const pct = ((completed / options.count) * 100).toFixed(2);
    const remaining = options.count - completed;
    const etaSec = Number(currentRps) > 0 ? remaining / Number(currentRps) : 0;
    const etaStr = formatSeconds(etaSec);

    process.stdout.write(
      `\r[SEED] ${completed.toLocaleString()}/${options.count.toLocaleString()} (${pct}%) | ` +
      `${currentRps} itens/s | Sucessos: ${succeeded.toLocaleString()} | Falhas: ${failed.toLocaleString()} | ETA: ${etaStr}   `
    );
  }

  async function worker() {
    while (!stopRequested) {
      let thisBatchSize = 1;

      if (isBatch) {
        if (dispatched >= options.count) break;
        thisBatchSize = Math.min(options.batchSize, options.count - dispatched);
        dispatched += thisBatchSize;
      } else {
        const idx = currentIndex++;
        if (idx >= options.count) break;
        thisBatchSize = 1;
      }

      const payload = isBatch
        ? Array.from({ length: thisBatchSize }, () => generateMockReceivable(merchants))
        : generateMockReceivable(merchants);

      try {
        const response = await fetch(endpoint, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload),
        });

        completed += thisBatchSize;
        if (response.status === 201) {
          succeeded += thisBatchSize;
        } else {
          failed += thisBatchSize;
          const body = await response.text();
          if (errors.length < 5) {
            errors.push(`Status ${response.status}: ${body.substring(0, 100)}`);
          }
        }
      } catch (err) {
        completed += thisBatchSize;
        failed += thisBatchSize;
        if (errors.length < 5) {
          errors.push(err.message || 'Connection error');
        }
      }

      logProgress();
    }
  }

  const workers = Array.from({ length: options.concurrency }, () => worker());
  await Promise.all(workers);
  process.removeListener('SIGINT', sigintHandler);
  logProgress(true);

  const durationSec = ((Date.now() - startTime) / 1000).toFixed(2);
  const rps = (completed / Math.max(0.001, parseFloat(durationSec))).toFixed(1);

  console.log(`\n\n============================================================`);
  console.log(`[SEED] Finalizado em ${durationSec}s (${rps} itens/s)`);
  console.log(`[SEED] Modo:        ${isBatch ? `Batch (${options.batchSize} itens/lote)` : 'Individual'}`);
  console.log(`[SEED] Processados: ${completed.toLocaleString()} de ${options.count.toLocaleString()}`);
  console.log(`[SEED] Sucessos:    ${succeeded.toLocaleString()} (HTTP 201 Created)`);
  console.log(`[SEED] Falhas:      ${failed.toLocaleString()}`);
  if (failed > 0) {
    console.log(`[SEED] Primeiros erros encontrados:`);
    errors.forEach((err) => console.log(`   - ${err}`));
    if (errors.some((e) => e.includes('ECONNREFUSED') || e.includes('fetch failed'))) {
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
// SQL File Seeder (Streaming to Disk with Backpressure & O(1) Memory)
// ============================================================================
async function runSqlSeeder(options, merchants) {
  const filePath = resolve(process.cwd(), options.output);
  console.log(`\n============================================================`);
  console.log(`[SEED] Modo SQL: Gerando ${options.count.toLocaleString()} recebíveis em arquivo`);
  console.log(`[SEED] Arquivo:  ${filePath}`);
  console.log(`[SEED] Lojistas: ${merchants.join(', ')}`);
  console.log(`[SEED] Memória:  O(1) — Gravação contínua em stream com backpressure`);
  console.log(`============================================================\n`);

  const startTime = Date.now();
  let lastLogTime = 0;
  const stream = createWriteStream(filePath, { flags: 'w', encoding: 'utf8' });

  function writeAsync(data) {
    return new Promise((resolve, reject) => {
      const onError = (err) => {
        cleanup();
        reject(err);
      };
      const onDrain = () => {
        cleanup();
        resolve();
      };
      const cleanup = () => {
        stream.removeListener('error', onError);
        stream.removeListener('drain', onDrain);
      };

      stream.once('error', onError);

      if (!stream.write(data)) {
        stream.once('drain', onDrain);
      } else {
        cleanup();
        resolve();
      }
    });
  }

  // Header
  await writeAsync(
    `-- ============================================================================\n` +
    `-- Seed de Recebíveis gerado por js/seed.js em ${new Date().toISOString()}\n` +
    `-- PostgreSQL 18 nativo com uuidv7() e Keyset Pagination\n` +
    `-- Total de registros: ${options.count.toLocaleString()}\n` +
    `-- ============================================================================\n\n`
  );

  const BATCH_SIZE = 500;
  let written = 0;

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

    const sqlChunk =
      `INSERT INTO receivables (id, merchant_id, card_brand, gross_amount, fee_rate, fee_amount, net_amount, installments, installment_number, status, transaction_date, settlement_date, created_at) VALUES\n` +
      valueTuples.join(',\n') +
      `;\n`;

    await writeAsync(sqlChunk);
    written += batchCount;

    const now = Date.now();
    if (now - lastLogTime > 500 || written === options.count) {
      lastLogTime = now;
      const pct = ((written / options.count) * 100).toFixed(2);
      const elapsed = (now - startTime) / 1000;
      const itemsSec = (written / Math.max(0.001, elapsed)).toFixed(0);
      process.stdout.write(`\r[SEED] Gravando SQL: ${written.toLocaleString()}/${options.count.toLocaleString()} (${pct}%) | ${itemsSec} registros/s`);
    }
  }

  await new Promise((resolve) => stream.end(resolve));

  const durationSec = ((Date.now() - startTime) / 1000).toFixed(2);
  const itemsSec = (options.count / Math.max(0.001, parseFloat(durationSec))).toFixed(0);

  console.log(`\n\n[SEED] Arquivo SQL gerado com sucesso: ${filePath}`);
  console.log(`[SEED] Registros gerados: ${options.count.toLocaleString()}`);
  console.log(`[SEED] Tempo decorrido:   ${durationSec}s (${itemsSec} registros/s)`);
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
    await runSqlSeeder(options, merchants);
  } else {
    await runHttpSeeder(options, merchants);
  }
}

main().catch((err) => {
  console.error('[ERRO FATAL]', err);
  process.exit(1);
});
