-- Local-development bootstrap: create the least-privilege database identities
-- described in mysql-design §14. Runs once on first MySQL container boot.
-- Production provisions these accounts through infrastructure-as-code, not this file.

-- Flyway migration identity: full DDL on the schema (used only during migration).
CREATE USER IF NOT EXISTS 'marketplace_flyway'@'%' IDENTIFIED BY 'flyway_dev_only';
GRANT ALL PRIVILEGES ON archims_techcorner.* TO 'marketplace_flyway'@'%';

-- Application runtime identity: DML only, NO DDL, NO account admin, NO file access.
CREATE USER IF NOT EXISTS 'marketplace_app'@'%' IDENTIFIED BY 'app_dev_only';
GRANT SELECT, INSERT, UPDATE, DELETE ON archims_techcorner.* TO 'marketplace_app'@'%';

-- Read-only analytics identity (reserved for Python services; no writes ever).
CREATE USER IF NOT EXISTS 'marketplace_analytics'@'%' IDENTIFIED BY 'analytics_dev_only';
GRANT SELECT ON archims_techcorner.* TO 'marketplace_analytics'@'%';

FLUSH PRIVILEGES;
