-- T14/T18: Core order tables for parent + seller orders, immutable snapshots.
-- Follows mysql-design: parent_order, seller_order, order_item with snapshot data (price, name at time).
-- DECIMAL for money, public_id, status.

CREATE TABLE parent_orders (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id BINARY(16) NOT NULL UNIQUE,
    user_id BIGINT UNSIGNED NOT NULL,
    total_amount DECIMAL(15,2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL DEFAULT 'PHP',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    CONSTRAINT fk_parent_order_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_parent_order_user ON parent_orders(user_id);
CREATE INDEX idx_parent_order_status ON parent_orders(status);

CREATE TABLE seller_orders (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id BINARY(16) NOT NULL UNIQUE,
    parent_order_id BIGINT UNSIGNED NOT NULL,
    seller_id BIGINT UNSIGNED NOT NULL,
    subtotal DECIMAL(15,2) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(6) NOT NULL,

    CONSTRAINT fk_seller_order_parent FOREIGN KEY (parent_order_id) REFERENCES parent_orders(id),
    CONSTRAINT fk_seller_order_seller FOREIGN KEY (seller_id) REFERENCES sellers(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE order_items (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    seller_order_id BIGINT UNSIGNED NOT NULL,
    variant_id BIGINT UNSIGNED NOT NULL,
    product_name_snapshot VARCHAR(255) NOT NULL,  -- immutable
    variant_name_snapshot VARCHAR(255),
    sku_snapshot VARCHAR(100),
    unit_price_snapshot DECIMAL(15,2) NOT NULL,
    quantity INT NOT NULL,
    line_total DECIMAL(15,2) NOT NULL,

    CONSTRAINT fk_order_item_seller_order FOREIGN KEY (seller_order_id) REFERENCES seller_orders(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
