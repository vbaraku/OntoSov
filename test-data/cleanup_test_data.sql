-- ============================================================================
-- OntoSov Scalability Test Data Cleanup Script
-- ============================================================================
-- This script removes all test data inserted by the populate scripts.
-- Run the appropriate section for each database.
--
-- WARNING: This will permanently delete all test data!
-- ============================================================================

-- ============================================================================
-- SECTION 1: PostgreSQL (E-commerce Database) Cleanup
-- ============================================================================
-- Run this section against the ecommerce_db PostgreSQL database

-- Delete orders first (foreign key dependency)
DELETE FROM order_history WHERE order_id >= 9900000 AND order_id <= 9999999;

-- Delete test user profiles
DELETE FROM user_profiles WHERE tax_id LIKE 'TEST%';

-- Alternative: Delete by user_id range
-- DELETE FROM user_profiles WHERE user_id >= 9990010 AND user_id <= 9910000;

-- Delete test products
DELETE FROM products WHERE product_id >= 990001 AND product_id <= 990500;

-- Verification query for PostgreSQL:
-- SELECT 'user_profiles' as table_name, COUNT(*) as test_records FROM user_profiles WHERE tax_id LIKE 'TEST%'
-- UNION ALL
-- SELECT 'order_history', COUNT(*) FROM order_history WHERE order_id >= 9900000
-- UNION ALL
-- SELECT 'products', COUNT(*) FROM products WHERE product_id >= 990001 AND product_id <= 990500;


-- ============================================================================
-- SECTION 2: MySQL (Healthcare Database) Cleanup
-- ============================================================================
-- Run this section against the health_research_center MySQL database

-- Delete medical records first (foreign key dependency)
DELETE FROM medical_records WHERE record_id >= 9900000 AND record_id <= 9999999;

-- Delete test patients
DELETE FROM patients WHERE tax_identifier LIKE 'TEST%';

-- Alternative: Delete by patient_id range
-- DELETE FROM patients WHERE patient_id >= 9990010 AND patient_id <= 9910000;

-- Verification query for MySQL:
-- SELECT 'patients' as table_name, COUNT(*) as test_records FROM patients WHERE tax_identifier LIKE 'TEST%'
-- UNION ALL
-- SELECT 'medical_records', COUNT(*) FROM medical_records WHERE record_id >= 9900000;


-- ============================================================================
-- Quick Cleanup Commands (Copy-Paste Ready)
-- ============================================================================

-- PostgreSQL (run all at once):
/*
BEGIN;
DELETE FROM order_history WHERE order_id >= 9900000 AND order_id <= 9999999;
DELETE FROM user_profiles WHERE tax_id LIKE 'TEST%';
DELETE FROM products WHERE product_id >= 990001 AND product_id <= 990500;
COMMIT;
*/

-- MySQL (run all at once):
/*
START TRANSACTION;
DELETE FROM medical_records WHERE record_id >= 9900000 AND record_id <= 9999999;
DELETE FROM patients WHERE tax_identifier LIKE 'TEST%';
COMMIT;
*/
