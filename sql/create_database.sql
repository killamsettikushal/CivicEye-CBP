-- ============================================================
-- CivicEye - Civic Issue Reporting and Management System
-- Script : create_database.sql
-- Purpose: Creates the application database (run once).
--          Safe to re-run; will not drop an existing database.
-- ============================================================

CREATE DATABASE IF NOT EXISTS civiceye_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- Switch to the database for subsequent scripts.
USE civiceye_db;
