USE archims_techcorner;

-- Create Customer
SET @customerId = 1;
SET @customerRoleId = (SELECT id FROM roles WHERE code = 'CUSTOMER');

INSERT INTO users (id, public_id, email_normalized, email_display, password_hash, password_algo, status, created_at, updated_at, security_version) 
VALUES (@customerId, UUID_TO_BIN(UUID()), 'customer@example.com', 'customer@example.com', '{bcrypt}$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HCGFJZ/.4wN3R.U2N/8.e', 'bcrypt', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
ON DUPLICATE KEY UPDATE email_normalized = email_normalized;

INSERT INTO customer_profiles (public_id, user_id, display_name, locale, timezone, created_at, updated_at)
VALUES (UUID_TO_BIN(UUID()), @customerId, 'Test Customer', 'en', 'UTC', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE display_name = display_name;

INSERT INTO user_roles (user_id, role_id, active_marker, granted_at)
VALUES (@customerId, @customerRoleId, 1, UTC_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE granted_at = granted_at;

-- Create Admin
SET @adminId = 2;
SET @adminRoleId = (SELECT id FROM roles WHERE code = 'ADMIN');

INSERT INTO users (id, public_id, email_normalized, email_display, password_hash, password_algo, status, created_at, updated_at, security_version) 
VALUES (@adminId, UUID_TO_BIN(UUID()), 'admin@example.com', 'admin@example.com', '{bcrypt}$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HCGFJZ/.4wN3R.U2N/8.e', 'bcrypt', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
ON DUPLICATE KEY UPDATE email_normalized = email_normalized;

INSERT INTO user_roles (user_id, role_id, active_marker, granted_at)
VALUES (@adminId, @adminRoleId, 1, UTC_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE granted_at = granted_at;
