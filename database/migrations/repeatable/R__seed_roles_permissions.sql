-- =============================================================================
-- Repeatable seed of the RBAC vocabulary (roles, permissions, role->permission).
-- Idempotent: safe to re-run. Flyway re-applies this whenever its checksum changes.
-- Source of truth for the mapping: documentation/security/rbac-matrix.md.
-- This seeds the identity/access set in full plus representative anchors from every
-- other domain so separation-of-duties is demonstrable; the complete ~150-permission
-- matrix is generated from rbac-matrix.md as the platform grows.
-- =============================================================================

-- --- Roles (the six platform roles) ---
INSERT INTO roles (code, name, is_active, created_at, updated_at) VALUES
    ('CUSTOMER', 'Customer',              TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('SELLER',   'Seller',                TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('ADMIN',    'Administrator',         TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('SUPPORT',  'Support Agent',         TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('FINANCE',  'Finance Administrator', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('CONTENT',  'Content Manager',       TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE name = VALUES(name), is_active = VALUES(is_active), updated_at = UTC_TIMESTAMP(6);

-- --- Permissions (module.action) ---
INSERT INTO permissions (code, module, description, is_active, created_at) VALUES
    -- identity & access
    ('users.read.any',          'users',   'Read any user record',                  TRUE, UTC_TIMESTAMP(6)),
    ('users.search',            'users',   'Search users',                          TRUE, UTC_TIMESTAMP(6)),
    ('users.pii.read',          'users',   'Read sensitive user PII (audited)',     TRUE, UTC_TIMESTAMP(6)),
    ('users.pii.export',        'users',   'Export user PII (step-up)',             TRUE, UTC_TIMESTAMP(6)),
    ('users.role.assign',       'users',   'Assign a platform role',                TRUE, UTC_TIMESTAMP(6)),
    ('users.role.revoke',       'users',   'Revoke a platform role',                TRUE, UTC_TIMESTAMP(6)),
    ('users.lock',              'users',   'Administratively lock an account',      TRUE, UTC_TIMESTAMP(6)),
    ('users.unlock',            'users',   'Administratively unlock an account',    TRUE, UTC_TIMESTAMP(6)),
    ('users.suspend',           'users',   'Suspend an account',                    TRUE, UTC_TIMESTAMP(6)),
    ('users.reinstate',         'users',   'Reinstate a suspended account',         TRUE, UTC_TIMESTAMP(6)),
    ('users.impersonate',       'users',   'Impersonate a user (step-up + dual)',   TRUE, UTC_TIMESTAMP(6)),
    ('users.erasure.execute',   'users',   'Execute a data-erasure request',        TRUE, UTC_TIMESTAMP(6)),
    ('roles.read',              'users',   'Read the role catalog',                 TRUE, UTC_TIMESTAMP(6)),
    ('permissions.read',        'users',   'Read the permission catalog',           TRUE, UTC_TIMESTAMP(6)),
    ('login_history.read.any',  'users',   'Read any login history',                TRUE, UTC_TIMESTAMP(6)),
    ('audit.read',              'auditlogs','Read the audit log',                    TRUE, UTC_TIMESTAMP(6)),
    ('audit.export',            'auditlogs','Export the audit log (step-up + dual)', TRUE, UTC_TIMESTAMP(6)),
    -- seller & store
    ('seller.approve',          'sellers', 'Approve a seller application',          TRUE, UTC_TIMESTAMP(6)),
    ('seller.reject',           'sellers', 'Reject a seller application',           TRUE, UTC_TIMESTAMP(6)),
    ('seller.suspend',          'sellers', 'Suspend a seller',                      TRUE, UTC_TIMESTAMP(6)),
    ('seller.reinstate',        'sellers', 'Reinstate a seller',                    TRUE, UTC_TIMESTAMP(6)),
    -- catalog
    ('product.moderate',        'products','Approve/reject a product',              TRUE, UTC_TIMESTAMP(6)),
    ('product.remove',          'products','Take down a product',                   TRUE, UTC_TIMESTAMP(6)),
    ('category.manage',         'categories','Manage the category tree',            TRUE, UTC_TIMESTAMP(6)),
    ('brand.create',            'brands',  'Create a brand',                        TRUE, UTC_TIMESTAMP(6)),
    -- orders
    ('order.read.any',          'orders',  'Read any order',                        TRUE, UTC_TIMESTAMP(6)),
    ('order.search',            'orders',  'Search all orders',                     TRUE, UTC_TIMESTAMP(6)),
    ('order.override',          'orders',  'Force an order state (step-up)',        TRUE, UTC_TIMESTAMP(6)),
    -- money (separation of duties)
    ('refund.approve.standard', 'refunds', 'Approve a standard refund',             TRUE, UTC_TIMESTAMP(6)),
    ('refund.approve.large',    'refunds', 'Approve a large refund (dual)',         TRUE, UTC_TIMESTAMP(6)),
    ('payment.reconcile',       'payments','Reconcile payments',                    TRUE, UTC_TIMESTAMP(6)),
    ('commission.rule.manage',  'sellerbalances','Manage commission rules',         TRUE, UTC_TIMESTAMP(6)),
    ('payout.approve',          'sellerpayouts','Approve a payout (step-up + dual)',TRUE, UTC_TIMESTAMP(6)),
    ('payout.reject',           'sellerpayouts','Reject a payout',                  TRUE, UTC_TIMESTAMP(6)),
    ('payout.freeze',           'sellerpayouts','Freeze payouts',                   TRUE, UTC_TIMESTAMP(6)),
    ('financial.report.read',   'sellerbalances','Read financial reports',          TRUE, UTC_TIMESTAMP(6)),
    ('dashboard.finance.read',  'auditlogs','Read the finance dashboard',           TRUE, UTC_TIMESTAMP(6)),
    ('dashboard.marketplace.read','auditlogs','Read the marketplace dashboard',     TRUE, UTC_TIMESTAMP(6)),
    -- fulfillment / trust
    ('dispute.arbitrate',       'returns', 'Arbitrate a dispute (step-up)',         TRUE, UTC_TIMESTAMP(6)),
    ('review.moderate',         'reviews', 'Moderate reviews',                      TRUE, UTC_TIMESTAMP(6)),
    -- content
    ('content.banner.manage',   'notifications','Manage homepage banners',          TRUE, UTC_TIMESTAMP(6)),
    ('content.landing.manage',  'notifications','Manage landing pages',             TRUE, UTC_TIMESTAMP(6)),
    ('content.faq.manage',      'notifications','Manage the help center / FAQ',      TRUE, UTC_TIMESTAMP(6)),
    ('content.publish',         'notifications','Publish content',                  TRUE, UTC_TIMESTAMP(6)),
    ('content.policy.publish',  'notifications','Publish marketplace policy (step-up)', TRUE, UTC_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE module = VALUES(module), description = VALUES(description), is_active = VALUES(is_active);

-- --- Role -> permission grants (only the "A" cells from rbac-matrix §5; conditional/
--     delegable cells are gated in the application service, not seeded as blanket grants) ---
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON (
    (r.code = 'ADMIN' AND p.code IN (
        'users.read.any','users.search','users.pii.read','users.pii.export',
        'users.role.assign','users.role.revoke','users.lock','users.unlock',
        'users.suspend','users.reinstate','users.impersonate','users.erasure.execute',
        'roles.read','permissions.read','login_history.read.any','audit.read','audit.export',
        'seller.approve','seller.reject','seller.suspend','seller.reinstate',
        'product.moderate','product.remove','category.manage','brand.create',
        'order.read.any','order.search','order.override',
        'refund.approve.standard','commission.rule.manage','payout.freeze',
        'dashboard.marketplace.read','dispute.arbitrate','review.moderate','content.policy.publish'
    ))
    OR (r.code = 'SUPPORT' AND p.code IN (
        'users.read.any','users.search','order.read.any','order.search'
    ))
    OR (r.code = 'FINANCE' AND p.code IN (
        'refund.approve.standard','refund.approve.large','payment.reconcile',
        'commission.rule.manage','payout.approve','payout.reject','payout.freeze',
        'financial.report.read','dashboard.finance.read'
    ))
    OR (r.code = 'CONTENT' AND p.code IN (
        'content.banner.manage','content.landing.manage','content.faq.manage','content.publish'
    ))
);
