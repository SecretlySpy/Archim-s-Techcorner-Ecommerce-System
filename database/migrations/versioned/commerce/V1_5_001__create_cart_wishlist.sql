-- =============================================================================
-- Shopping Cart + Wishlist: per-user cart items (variant-level) and wishlist (product-level).
-- Owning Java feature: cart.
-- cart_items holds one row per (user, variant) with adjustable quantity.
-- wishlist_items holds one row per (user, product) — a "save for later" list.
-- Multi-seller: a single cart can hold variants from different sellers.
-- Conventions match existing commerce tables: BIGINT UNSIGNED ids, InnoDB, utf8mb4.
-- =============================================================================

CREATE TABLE cart_items (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT UNSIGNED NOT NULL,
    variant_id      BIGINT UNSIGNED NOT NULL,
    quantity        INT             NOT NULL DEFAULT 1,
    added_at        DATETIME(6)     NOT NULL,
    updated_at      DATETIME(6)     NOT NULL,

    CONSTRAINT uk_cart__user_variant UNIQUE (user_id, variant_id),
    CONSTRAINT fk_cart__user    FOREIGN KEY (user_id)    REFERENCES users (id),
    CONSTRAINT fk_cart__variant FOREIGN KEY (variant_id) REFERENCES product_variants (id),
    CONSTRAINT ck_cart__qty     CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_cart__user ON cart_items (user_id);


CREATE TABLE wishlist_items (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT UNSIGNED NOT NULL,
    product_id      BIGINT UNSIGNED NOT NULL,
    added_at        DATETIME(6)     NOT NULL,

    CONSTRAINT uk_wishlist__user_product UNIQUE (user_id, product_id),
    CONSTRAINT fk_wishlist__user    FOREIGN KEY (user_id)    REFERENCES users (id),
    CONSTRAINT fk_wishlist__product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_wishlist__user ON wishlist_items (user_id);
