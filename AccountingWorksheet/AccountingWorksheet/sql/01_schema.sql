-- ============================================================================
--  Accounting Worksheet System - Database schema
--  Target: MySQL 8.0.16+ (CHECK constraints are enforced from 8.0.16 onward)
--  Run order: 01_schema.sql -> 02_chart_of_accounts.sql -> (optional) 03_sample_data.sql
-- ============================================================================

CREATE DATABASE IF NOT EXISTS accounting_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE accounting_db;

-- ----------------------------------------------------------------------------
-- Chart of accounts (FIXED: loaded by script, no add/edit/delete in the app)
-- `statement` is generated from the account type, so it can never be mistyped:
--   REVENUE / EXPENSE            -> IS (Income Statement)
--   ASSET / LIABILITY / EQUITY   -> BS (Balance Sheet)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS accounts (
  account_code  VARCHAR(10)  NOT NULL,
  account_title VARCHAR(100) NOT NULL,
  account_type  ENUM('ASSET','LIABILITY','EQUITY','REVENUE','EXPENSE') NOT NULL,
  normal_side   ENUM('DEBIT','CREDIT') NOT NULL,
  statement     CHAR(2) AS (
                  CASE WHEN account_type IN ('REVENUE','EXPENSE') THEN 'IS' ELSE 'BS' END
                ) STORED,
  PRIMARY KEY (account_code),
  UNIQUE KEY uq_account_title (account_title)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- Journal entry header. entry_type separates regular entries from adjusting
-- entries so both live in the same tables (needed for the 12-column worksheet).
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS journal_entries (
  entry_id    INT          NOT NULL AUTO_INCREMENT,
  entry_date  DATE         NOT NULL,
  description VARCHAR(255) NOT NULL,
  entry_type  ENUM('REGULAR','ADJUSTING') NOT NULL DEFAULT 'REGULAR',
  created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (entry_id),
  KEY idx_entry_date (entry_date),
  KEY idx_entry_type (entry_type)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- Journal entry lines. Exactly one of debit / credit is > 0 on every line.
-- Debits = credits per entry is enforced by the Java service layer.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS journal_lines (
  line_id      INT           NOT NULL AUTO_INCREMENT,
  entry_id     INT           NOT NULL,
  account_code VARCHAR(10)   NOT NULL,
  debit        DECIMAL(15,2) NOT NULL DEFAULT 0.00,
  credit       DECIMAL(15,2) NOT NULL DEFAULT 0.00,
  PRIMARY KEY (line_id),
  KEY idx_line_account (account_code),
  CONSTRAINT fk_line_entry   FOREIGN KEY (entry_id)
      REFERENCES journal_entries (entry_id) ON DELETE CASCADE,
  CONSTRAINT fk_line_account FOREIGN KEY (account_code)
      REFERENCES accounts (account_code),
  CONSTRAINT chk_one_side CHECK (
      (debit > 0 AND credit = 0) OR (debit = 0 AND credit > 0))
) ENGINE=InnoDB;
