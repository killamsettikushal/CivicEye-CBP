-- ============================================================
-- CivicEye — Database Migration Script
-- Script  : migrate_remove_reward_points.sql
-- Purpose : Drops the reward_points column from the users table.
--           Run this ONCE on your live civiceye_db to complete
--           the removal of the reward-points feature.
--
-- IMPORTANT
--   • Back up your database before running this script.
--   • This change is IRREVERSIBLE — the column and its data
--     will be permanently deleted.
--   • Verify that the updated Java application (which no longer
--     reads or writes reward_points) is deployed before running.
--   • Run with a DB account that has ALTER TABLE privilege.
--
-- Run command (MySQL CLI):
--   mysql -u root -p civiceye_db < migrate_remove_reward_points.sql
-- ============================================================

USE civiceye_db;

-- Safety check: only drop if the column still exists
-- (MySQL 8.0.4+ supports IF EXISTS on DROP COLUMN)
ALTER TABLE users
    DROP COLUMN IF EXISTS reward_points;

-- Verify the column is gone
SELECT
    COLUMN_NAME,
    DATA_TYPE,
    IS_NULLABLE,
    COLUMN_DEFAULT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'civiceye_db'
  AND TABLE_NAME   = 'users'
ORDER BY ORDINAL_POSITION;
