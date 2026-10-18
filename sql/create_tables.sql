-- ============================================================
-- CivicEye - Civic Issue Reporting and Management System
-- Script : create_tables.sql
-- Purpose: Creates all tables for civiceye_db.
--          Run after create_database.sql.
--          Safe to re-run (uses IF NOT EXISTS).
-- ============================================================

USE civiceye_db;

-- ------------------------------------------------------------
-- Table: users
-- Stores both Citizens and Admins.
-- Passwords are stored as SHA-256 hash + independent salt.
-- The Java application performs all hashing; MySQL stores
-- only the resulting hex strings.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    user_id       INT             NOT NULL AUTO_INCREMENT,
    name          VARCHAR(100)    NOT NULL,
    email         VARCHAR(150)    NOT NULL,
    password_hash VARCHAR(64)     NOT NULL,   -- SHA-256 hex (64 chars)
    salt          VARCHAR(32)     NOT NULL,   -- random hex salt (16 bytes = 32 hex chars)
    role          ENUM('Citizen', 'Admin') NOT NULL DEFAULT 'Citizen',
    created_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (user_id),
    UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


-- ------------------------------------------------------------
-- Table: issues
-- Each issue is submitted by exactly one registered Citizen.
-- Status transitions: Pending → In Progress → Resolved
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS issues (
    issue_id    INT             NOT NULL AUTO_INCREMENT,
    reporter_id INT             NOT NULL,
    category    VARCHAR(50)     NOT NULL,
    location    VARCHAR(200)    NOT NULL,
    description TEXT            NOT NULL,
    status      ENUM('Pending', 'In Progress', 'Resolved')
                                NOT NULL DEFAULT 'Pending',
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (issue_id),
    CONSTRAINT fk_issues_reporter
        FOREIGN KEY (reporter_id)
        REFERENCES users (user_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    INDEX idx_issues_reporter  (reporter_id),
    INDEX idx_issues_status    (status),
    INDEX idx_issues_category  (category),
    INDEX idx_issues_created   (created_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
