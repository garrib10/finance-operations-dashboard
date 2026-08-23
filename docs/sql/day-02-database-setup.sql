-- ============================================================
-- Finance Operations Dashboard
-- Day 2 — MySQL Database Setup
--
-- Purpose:
-- Creates the local development database and a dedicated
-- application user for the Finance Operations Dashboard.
--
-- This script is intended for local development.
-- Do NOT store real passwords in version control.
-- ============================================================


-- ============================================================
-- STEP 1
-- Create the application database
-- ============================================================
--
-- utf8mb4:
--   Full Unicode support (recommended by MySQL)
--
-- utf8mb4_unicode_ci:
--   Case-insensitive Unicode collation
--
-- This ensures the application correctly stores international
-- characters and modern text.
-- ============================================================

CREATE DATABASE finance_operations_dashboard
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;


-- ============================================================
-- STEP 2
-- Verify the database was created successfully
-- ============================================================

SHOW DATABASES;


-- ============================================================
-- STEP 3
-- Create a dedicated MySQL application user
-- ============================================================
--
-- We intentionally avoid using the MySQL root account.
--
-- Using a dedicated application user follows the
-- Principle of Least Privilege.
--
-- Replace YOUR_SECURE_PASSWORD with your own local password.
-- ============================================================

CREATE USER 'finance_user'@'localhost'
IDENTIFIED BY 'YOUR_SECURE_PASSWORD';


-- ============================================================
-- STEP 4
-- Grant permissions
-- ============================================================
--
-- finance_operations_dashboard.*
--
-- Means:
-- Every table inside the finance_operations_dashboard database.
--
-- This user will NOT receive access to other databases.
-- ============================================================

GRANT ALL PRIVILEGES
ON finance_operations_dashboard.*
TO 'finance_user'@'localhost';


-- ============================================================
-- STEP 5
-- Reload privilege information
-- ============================================================

FLUSH PRIVILEGES;


-- ============================================================
-- STEP 6
-- Verify the application user exists
-- ============================================================

SELECT user,
       host
FROM mysql.user
WHERE user = 'finance_user';


-- ============================================================
-- STEP 7
-- Exit the MySQL root session
-- ============================================================

EXIT;


-- ============================================================
-- STEP 8
-- Log back in as the application user
--
-- This command is executed from the terminal,
-- not from inside the MySQL prompt.
--
-- mysql -u finance_user -p
-- ============================================================


-- ============================================================
-- STEP 9
-- Select the application database
-- ============================================================

USE finance_operations_dashboard;


-- ============================================================
-- STEP 10
-- Verify the selected database
-- ============================================================

SELECT DATABASE();


-- Expected Output:
--
-- finance_operations_dashboard


-- ============================================================
-- STEP 11
-- Verify the database is currently empty
-- ============================================================

SHOW TABLES;


-- Expected Output:
--
-- Empty set
--
-- This is correct because no JPA entities have been created yet.
--
-- BaseEntity is a @MappedSuperclass and does not generate
-- a database table.
--
-- The first table will be created on Day 3 when we introduce
-- the User entity.


-- ============================================================
-- DAY 2 SUMMARY
-- ============================================================
--
-- ✔ Created finance_operations_dashboard database
-- ✔ Created finance_user
-- ✔ Granted database permissions
-- ✔ Verified application user access
-- ✔ Confirmed database is empty
--
-- Spring Boot now connects successfully using:
--
-- Spring Boot
--      ↓
-- Spring Data JPA
--      ↓
-- Hibernate
--      ↓
-- HikariCP
--      ↓
-- MySQL Connector/J
--      ↓
-- finance_operations_dashboard
--
-- Day 3 begins by creating the first concrete JPA entity:
--
-- User
--
-- Hibernate will then generate the first database table.
-- ============================================================