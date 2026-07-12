-- =============================================================================
-- Inventory: per-warehouse, per-variant stock buckets + an immutable adjustment ledger.
-- Owning Java feature: inventory. Source of truth for sellable stock (mysql-design §6.4, §9).
-- sellable = on_hand_quantity - reserved_quantity. Reservations arrive with checkout (future).
-- Conventions match the existing commerce tables: BIGINT UNSIGNED ids, InnoDB, utf8mb4.
-- =============================================================================
CREATE TABLE inventory (
    id                 BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    warehouse_id       BIGINT UNSIGNED NOT NULL,
    variant_id         BIGINT UNSIGNED NOT NULL,
    on_hand_quantity   INT             NOT NULL DEFAULT 0,
    reserved_quantity  INT             NOT NULL DEFAULT 0,
    reorder_threshold  INT             NOT NULL DEFAULT 0,
    version            BIGINT          NOT NULL DEFAULT 0,   -- optimistic lock
    created_at         DATETIME(6)     NOT NULL,
    updated_at         DATETIME(6)     NOT NULL,
    CONSTRAINT uk_inventory__wh_variant UNIQUE (warehouse_id, variant_id),
    CONSTRAINT fk_inventory__warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT fk_inventory__variant   FOREIGN KEY (variant_id)   REFERENCES product_variants (id),
    CONSTRAINT ck_inventory__on_hand   CHECK (on_hand_quantity >= 0),
    CONSTRAINT ck_inventory__reserved  CHECK (reserved_quantity >= 0 AND reserved_quantity <= on_hand_quantity),
    CONSTRAINT ck_inventory__reorder   CHECK (reorder_threshold >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_inventory__variant  ON inventory (variant_id);
CREATE INDEX idx_inventory__warehouse ON inventory (warehouse_id);

-- Immutable movement log: every stock change records its signed delta and before/after quantities.
CREATE TABLE inventory_adjustments (
    id               BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    inventory_id     BIGINT UNSIGNED NOT NULL,
    delta            INT             NOT NULL,               -- signed, non-zero
    reason_code      VARCHAR(40)     NOT NULL,               -- MANUAL_SET, MANUAL_ADJUST, RECEIVE, DAMAGE, ...
    quantity_before  INT             NOT NULL,
    quantity_after   INT             NOT NULL,
    actor_user_id    BIGINT UNSIGNED NULL,
    created_at       DATETIME(6)     NOT NULL,
    CONSTRAINT fk_inv_adj__inventory FOREIGN KEY (inventory_id)  REFERENCES inventory (id),
    CONSTRAINT fk_inv_adj__actor     FOREIGN KEY (actor_user_id) REFERENCES users (id),
    CONSTRAINT ck_inv_adj__nonzero   CHECK (delta <> 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_inv_adj__inventory ON inventory_adjustments (inventory_id, created_at, id);
