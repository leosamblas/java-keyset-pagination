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
