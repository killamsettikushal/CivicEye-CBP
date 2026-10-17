-- ============================================================
-- CivicEye - Civic Issue Reporting and Management System
-- Script : insert_sample_data.sql
-- Purpose: Populates tables with demo accounts and issues.
--          Run after create_tables.sql.
--
-- Demo Credentials
-- ─────────────────────────────────────────────────────────────
--  Role     │ Email                    │ Password
--  ─────────┼──────────────────────────┼────────────────────
--  Admin    │ admin@civiceye.com       │ Admin@123
--  Citizen  │ citizen@civiceye.com     │ Citizen@123
-- ─────────────────────────────────────────────────────────────
--
-- How the hashes were produced (Java logic, for reference):
--   hash = SHA-256(password + salt)   [concatenation, not HMAC]
--   Stored as lowercase hex.
-- ============================================================

USE civiceye_db;

-- ----------------------------------------------------------
-- Demo users  (INSERT IGNORE to stay idempotent)
-- ----------------------------------------------------------
INSERT IGNORE INTO users
    (name, email, password_hash, salt, role, reward_points)
VALUES
    -- Admin@123  +  salt a1b2c3d4e5f60001
    (
        'System Administrator',
        'admin@civiceye.com',
        '01b79d242da80052277081ae43731cb97c916a0c1c68120358279dc98a278864',
        'a1b2c3d4e5f60001',
        'Admin',
        0
    ),
    -- Citizen@123  +  salt f0e1d2c3b4a50002
    (
        'Ravi Kumar',
        'citizen@civiceye.com',
        '1960dc3f041e1f50112ae112ace5bca50e628784061c74cc41b91eb053af057c',
        'f0e1d2c3b4a50002',
        'Citizen',
        15
    );

-- ----------------------------------------------------------
-- Sample issues submitted by the demo citizen (user_id = 2)
-- Uses a subquery so the foreign key always resolves correctly
-- even if auto-increment IDs differ.
-- ----------------------------------------------------------
INSERT IGNORE INTO issues
    (issue_id, reporter_id, category, location, description, status, created_at)
VALUES
    (1,
     (SELECT user_id FROM users WHERE email = 'citizen@civiceye.com'),
     'Roads',
     'MG Road, Bengaluru',
     'Large pothole near the traffic signal causing risk to two-wheelers.',
     'Pending',
     '2026-10-10 09:15:00'),

    (2,
     (SELECT user_id FROM users WHERE email = 'citizen@civiceye.com'),
     'Sanitation',
     'Koramangala 4th Block',
     'Garbage not collected for five consecutive days. Bin overflowing onto street.',
     'In Progress',
     '2026-10-12 14:30:00'),

    (3,
     (SELECT user_id FROM users WHERE email = 'citizen@civiceye.com'),
     'Street Lighting',
     'Indiranagar 100 Feet Road',
     'Three consecutive streetlights are non-functional after 8 PM.',
     'Resolved',
     '2026-10-14 18:00:00');
