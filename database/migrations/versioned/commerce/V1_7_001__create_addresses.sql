-- Addresses for customers (T12)
-- customer_addresses: user address book
-- order_addresses: immutable snapshots for orders

CREATE TABLE customer_addresses (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id BINARY(16) NOT NULL UNIQUE,
    user_id BIGINT UNSIGNED NOT NULL,
    address_type ENUM('SHIPPING', 'BILLING') NOT NULL DEFAULT 'SHIPPING',
    recipient_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    line1 VARCHAR(255) NOT NULL,
    line2 VARCHAR(255),
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100),
    postal_code VARCHAR(20) NOT NULL,
    country VARCHAR(2) NOT NULL DEFAULT 'PH',
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    CONSTRAINT fk_customer_address_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_customer_address_default UNIQUE (user_id, address_type, is_default)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_customer_address_user ON customer_addresses (user_id);
CREATE INDEX idx_customer_address_public ON customer_addresses (public_id);

-- For order snapshots (immutable)
CREATE TABLE order_addresses (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT UNSIGNED NOT NULL,  -- will reference parent_orders later
    address_type ENUM('SHIPPING', 'BILLING') NOT NULL,
    recipient_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    line1 VARCHAR(255) NOT NULL,
    line2 VARCHAR(255),
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100),
    postal_code VARCHAR(20) NOT NULL,
    country VARCHAR(2) NOT NULL,
    created_at DATETIME(6) NOT NULL,

    CONSTRAINT fk_order_address_order FOREIGN KEY (order_id) REFERENCES parent_orders (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
