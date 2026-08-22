-- ============================================================
-- Finance Operations Dashboard
-- Day 3 — User Registration Verification
--
-- Purpose:
-- Verifies that the users table was created correctly and that
-- user registration persists safe, normalized data to MySQL.
--
-- This script is intended for local development verification.
-- Do not include real passwords or sensitive data.
-- ============================================================


-- ============================================================
-- STEP 1
-- Select the application database
-- ============================================================

USE finance_operations_dashboard;


-- ============================================================
-- STEP 2
-- Verify that the users table exists
-- ============================================================

SHOW TABLES;


-- Expected:
-- users


-- ============================================================
-- STEP 3
-- Inspect the users table structure
-- ============================================================
--
-- Confirms:
-- - Primary key
-- - Auto-increment ID
-- - NOT NULL constraints
-- - Unique email constraint
-- - Inherited audit fields
-- ============================================================

DESCRIBE users;


-- Expected columns:
--
-- id
-- created_at
-- updated_at
-- email
-- first_name
-- last_name
-- password_hash


-- ============================================================
-- STEP 4
-- Inspect the complete table definition
-- ============================================================
--
-- Useful for confirming:
-- - PRIMARY KEY
-- - UNIQUE email index
-- - VARCHAR lengths
-- - Character set / collation
-- - InnoDB storage engine
-- ============================================================

SHOW CREATE TABLE users;


-- ============================================================
-- STEP 5
-- Verify persisted users
-- ============================================================
--
-- The password_hash column should contain a BCrypt hash.
-- It must NOT contain the original plaintext password.
-- ============================================================

SELECT
    id,
    first_name,
    last_name,
    email,
    password_hash,
    created_at,
    updated_at
FROM users;


-- Expected:
-- - Registered user is present
-- - email is normalized
-- - password_hash begins with a BCrypt prefix such as $2a$
-- - created_at is populated
-- - updated_at is populated


-- ============================================================
-- STEP 6
-- Verify total number of users
-- ============================================================
--
-- This is useful after duplicate-registration testing.
--
-- If the same email was submitted twice and the API correctly
-- returned HTTP 409 Conflict, only one row should exist for that
-- account.
-- ============================================================

SELECT COUNT(*) AS total_users
FROM users;


-- ============================================================
-- STEP 7
-- Verify a specific user by email
-- ============================================================
--
-- Replace the example email below with test data if needed.
-- Keep committed documentation fictional/demo-only.
-- ============================================================

SELECT
    id,
    email,
    created_at
FROM users
WHERE email = 'alex@example.com';


-- ============================================================
-- STEP 8
-- Optional duplicate-email verification
-- ============================================================
--
-- This query checks whether any email appears more than once.
-- The result should be empty because the users.email column has
-- a UNIQUE constraint.
-- ============================================================

SELECT
    email,
    COUNT(*) AS occurrences
FROM users
GROUP BY email
HAVING COUNT(*) > 1;


-- Expected:
-- Empty set


-- ============================================================
-- STEP 9
-- Verify normalized email storage
-- ============================================================
--
-- UserService converts registration email values to lowercase
-- and trims surrounding whitespace before persistence.
--
-- This query helps identify any unexpectedly mixed-case emails.
-- ============================================================

SELECT
    id,
    email
FROM users
WHERE email <> LOWER(email);


-- Expected:
-- Empty set


-- ============================================================
-- DAY 3 SUMMARY
-- ============================================================
--
-- ✔ Created the User JPA entity
-- ✔ Hibernate generated the users table
-- ✔ Verified BaseEntity fields were inherited
-- ✔ Added a UNIQUE constraint for email
-- ✔ Created UserRepository
-- ✔ Created registration request/response DTOs
-- ✔ Added Bean Validation
-- ✔ Added BCrypt password hashing
-- ✔ Added UserService registration logic
-- ✔ Added POST /api/auth/register
-- ✔ Verified HTTP 201 Created for successful registration
-- ✔ Verified HTTP 409 Conflict for duplicate email
-- ✔ Verified HTTP 400 Bad Request for invalid registration data
-- ✔ Verified stored passwords are BCrypt hashes
-- ✔ Verified duplicate registrations do not create extra rows
--
-- Registration flow:
--
-- POST /api/auth/register
--          ↓
-- RegisterRequest
--          ↓
-- Bean Validation
--          ↓
-- AuthController
--          ↓
-- UserService
--          ↓
-- Duplicate Email Check
--          ↓
-- BCrypt Password Hashing
--          ↓
-- UserRepository
--          ↓
-- Hibernate
--          ↓
-- MySQL
--          ↓
-- UserResponse
--
-- Day 4 will build on this foundation by adding:
--
-- - Login
-- - Spring Security
-- - JWT creation and validation
-- - Protected endpoints
-- - Structured authentication error responses
-- ============================================================