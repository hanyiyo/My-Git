-- Identity foundation migration. Apply with Flyway in the Spring Boot deployment.
CREATE TABLE accounts (
  id CHAR(36) PRIMARY KEY,
  identifier VARCHAR(320) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'active',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE refresh_sessions (
  id CHAR(36) PRIMARY KEY,
  account_id CHAR(36) NOT NULL,
  token_hash CHAR(64) NOT NULL UNIQUE,
  expires_at TIMESTAMP NOT NULL,
  revoked_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_refresh_account FOREIGN KEY (account_id) REFERENCES accounts(id)
);
CREATE INDEX idx_refresh_account ON refresh_sessions(account_id);
