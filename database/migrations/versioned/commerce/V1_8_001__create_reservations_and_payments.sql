-- =============================================================================
-- Reservations, Checkout Quotes, Orders Extension, Payments, Shipments & Ledger
-- Covers M04 (Quotes, Reservations, Orders), M05 (Payments), M06 (Shipments & Outbox),
-- and M07 (Refunds & Seller Ledger) to avoid migration churn.
-- =============================================================================

ALTER TABLE parent_orders ADD COLUMN idempotency_key VARCHAR(100) NULL UNIQUE AFTER status;

CREATE TABLE inventory_reservations (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id BINARY(16) NOT NULL UNIQUE,
    inventory_id BIGINT UNSIGNED NOT NULL,
    order_item_id BIGINT UNSIGNED NULL,
    order_id BIGINT UNSIGNED NOT NULL,
    quantity INT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_inv_res_inventory FOREIGN KEY (inventory_id) REFERENCES inventory(id),
    CONSTRAINT fk_inv_res_order FOREIGN KEY (order_id) REFERENCES parent_orders(id),
    CONSTRAINT ck_inv_res_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_inv_res_status_expires ON inventory_reservations(status, expires_at, id);
CREATE INDEX idx_inv_res_order ON inventory_reservations(order_id, status);

CREATE TABLE order_payments (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id BINARY(16) NOT NULL UNIQUE,
    order_id BIGINT UNSIGNED NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    provider VARCHAR(50) NOT NULL,
    provider_payment_id VARCHAR(100),
    amount DECIMAL(15,2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL DEFAULT 'PHP',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_order_payments_order FOREIGN KEY (order_id) REFERENCES parent_orders(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_order_payments_order ON order_payments(order_id, status);
CREATE INDEX idx_order_payments_provider ON order_payments(provider, provider_payment_id);

CREATE TABLE checkout_quotes (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id BINARY(16) NOT NULL UNIQUE,
    user_id BIGINT UNSIGNED NOT NULL,
    shipping_address_id BIGINT UNSIGNED NOT NULL,
    subtotal DECIMAL(15,2) NOT NULL,
    shipping_fee DECIMAL(15,2) NOT NULL,
    tax_amount DECIMAL(15,2) NOT NULL,
    total_amount DECIMAL(15,2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL DEFAULT 'PHP',
    items_json JSON NOT NULL,
    used TINYINT NOT NULL DEFAULT 0,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_checkout_quote_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_checkout_quotes_user ON checkout_quotes(user_id, used, expires_at);

CREATE TABLE shipments (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id BINARY(16) NOT NULL UNIQUE,
    seller_order_id BIGINT UNSIGNED NOT NULL,
    courier_code VARCHAR(50) NOT NULL,
    tracking_number VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DISPATCHED',
    shipped_at DATETIME(6) NOT NULL,
    delivered_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_shipments_seller_order FOREIGN KEY (seller_order_id) REFERENCES seller_orders(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_shipments_seller_order ON shipments(seller_order_id);
CREATE INDEX idx_shipments_tracking ON shipments(courier_code, tracking_number);

CREATE TABLE communication_outbox (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    processed_at DATETIME(6) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_comm_outbox_status ON communication_outbox(status, created_at);

CREATE TABLE seller_ledger_entries (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id BINARY(16) NOT NULL UNIQUE,
    seller_id BIGINT UNSIGNED NOT NULL,
    seller_order_id BIGINT UNSIGNED NULL,
    transaction_type VARCHAR(40) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL DEFAULT 'PHP',
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_seller_ledger_seller FOREIGN KEY (seller_id) REFERENCES sellers(id),
    CONSTRAINT fk_seller_ledger_order FOREIGN KEY (seller_order_id) REFERENCES seller_orders(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_seller_ledger_seller ON seller_ledger_entries(seller_id, created_at);

CREATE TABLE refund_requests (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id BINARY(16) NOT NULL UNIQUE,
    parent_order_id BIGINT UNSIGNED NOT NULL,
    seller_order_id BIGINT UNSIGNED NULL,
    requested_amount DECIMAL(15,2) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    approved_by_user_id BIGINT UNSIGNED NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_refund_req_parent FOREIGN KEY (parent_order_id) REFERENCES parent_orders(id),
    CONSTRAINT fk_refund_req_seller FOREIGN KEY (seller_order_id) REFERENCES seller_orders(id),
    CONSTRAINT fk_refund_req_approver FOREIGN KEY (approved_by_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_refund_req_order ON refund_requests(parent_order_id, status);
