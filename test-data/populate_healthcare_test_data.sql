-- ============================================================================
-- OntoSov Scalability Test Data - Healthcare Database (MySQL)
-- ============================================================================
-- This script populates test data for scalability testing of the OntoSov
-- federated query system using Ontop SPARQL-to-SQL translation.
--
-- Test Subjects:
--   TEST0000010 - Small (10 medical records)
--   TEST0000100 - Medium (100 medical records)
--   TEST0001000 - Large (1,000 medical records)
--   TEST0010000 - XLarge (10,000 medical records)
-- ============================================================================

-- ============================================================================
-- SECTION 1: Test Patients
-- ============================================================================
-- Note: tax_identifier should match ecommerce tax_id (9 char limit)
INSERT IGNORE INTO patients (patient_id, tax_identifier, given_name, family_name, email, birth_date) VALUES
(9990010, 'TST000010', 'Test', 'User Small', 'test.small@example.com', '1985-03-15'),
(9990100, 'TST000100', 'Test', 'User Medium', 'test.medium@example.com', '1978-07-22'),
(9991000, 'TST001000', 'Test', 'User Large', 'test.large@example.com', '1990-11-08'),
(9910000, 'TST010000', 'Test', 'User XLarge', 'test.xlarge@example.com', '1982-05-30');

-- ============================================================================
-- SECTION 2: Medical Records for TEST0000010 (Small - 10 records)
-- ============================================================================
INSERT IGNORE INTO medical_records (record_id, patient_id, health_condition, treatment, date) VALUES
(9900001, 9990010, 'Annual Physical Examination', 'General health assessment and blood work', '2024-01-15'),
(9900002, 9990010, 'Seasonal Allergies', 'Prescribed antihistamine medication', '2024-02-20'),
(9900003, 9990010, 'Upper Respiratory Infection', 'Rest, fluids, and OTC medication', '2024-03-10'),
(9900004, 9990010, 'Vitamin D Deficiency', 'Vitamin D3 supplementation 2000 IU daily', '2024-04-05'),
(9900005, 9990010, 'Routine Blood Pressure Check', 'Blood pressure within normal range', '2024-05-12'),
(9900006, 9990010, 'Minor Skin Rash', 'Topical hydrocortisone cream applied', '2024-06-08'),
(9900007, 9990010, 'Dental Cleaning', 'Professional cleaning and fluoride treatment', '2024-07-22'),
(9900008, 9990010, 'Eye Examination', 'Updated prescription for corrective lenses', '2024-08-14'),
(9900009, 9990010, 'Flu Vaccination', 'Annual influenza vaccine administered', '2024-09-25'),
(9900010, 9990010, 'Cholesterol Screening', 'Lipid panel results within healthy range', '2024-10-30');

-- ============================================================================
-- SECTION 3: Medical Records for TEST0000100 (Medium - 100 records)
-- Using procedure for efficient generation
-- ============================================================================
DELIMITER //

DROP PROCEDURE IF EXISTS GenerateMediumMedicalRecords//

CREATE PROCEDURE GenerateMediumMedicalRecords()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE record_id_val INT DEFAULT 9900011;
    DECLARE condition_val VARCHAR(255);
    DECLARE treatment_val VARCHAR(255);
    DECLARE record_date DATE;

    WHILE i <= 100 DO
        SET record_date = DATE_ADD('2023-01-01', INTERVAL (i * 3) DAY);

        CASE (i % 20)
            WHEN 0 THEN
                SET condition_val = 'Routine Physical Examination';
                SET treatment_val = 'Complete health assessment performed';
            WHEN 1 THEN
                SET condition_val = 'Hypertension Monitoring';
                SET treatment_val = 'Blood pressure medication adjusted';
            WHEN 2 THEN
                SET condition_val = 'Diabetes Type 2 Follow-up';
                SET treatment_val = 'Metformin dosage reviewed, HbA1c tested';
            WHEN 3 THEN
                SET condition_val = 'Lower Back Pain';
                SET treatment_val = 'Physical therapy referral, NSAIDs prescribed';
            WHEN 4 THEN
                SET condition_val = 'Migraine Headaches';
                SET treatment_val = 'Sumatriptan prescribed for acute episodes';
            WHEN 5 THEN
                SET condition_val = 'Anxiety Disorder';
                SET treatment_val = 'Cognitive behavioral therapy recommended';
            WHEN 6 THEN
                SET condition_val = 'Insomnia';
                SET treatment_val = 'Sleep hygiene counseling, melatonin suggested';
            WHEN 7 THEN
                SET condition_val = 'Gastroesophageal Reflux';
                SET treatment_val = 'Proton pump inhibitor prescribed';
            WHEN 8 THEN
                SET condition_val = 'Seasonal Flu';
                SET treatment_val = 'Oseltamivir prescribed, rest advised';
            WHEN 9 THEN
                SET condition_val = 'Urinary Tract Infection';
                SET treatment_val = 'Antibiotics prescribed for 7 days';
            WHEN 10 THEN
                SET condition_val = 'Eczema Flare-up';
                SET treatment_val = 'Topical corticosteroid cream applied';
            WHEN 11 THEN
                SET condition_val = 'Allergic Rhinitis';
                SET treatment_val = 'Nasal corticosteroid spray prescribed';
            WHEN 12 THEN
                SET condition_val = 'Asthma Management';
                SET treatment_val = 'Inhaler technique reviewed, peak flow monitored';
            WHEN 13 THEN
                SET condition_val = 'Hypothyroidism';
                SET treatment_val = 'Levothyroxine dosage optimized';
            WHEN 14 THEN
                SET condition_val = 'Iron Deficiency Anemia';
                SET treatment_val = 'Iron supplements prescribed with vitamin C';
            WHEN 15 THEN
                SET condition_val = 'Osteoporosis Screening';
                SET treatment_val = 'DEXA scan scheduled, calcium recommended';
            WHEN 16 THEN
                SET condition_val = 'Depression Follow-up';
                SET treatment_val = 'SSRI medication continued, therapy ongoing';
            WHEN 17 THEN
                SET condition_val = 'Carpal Tunnel Syndrome';
                SET treatment_val = 'Wrist splint provided, ergonomic advice given';
            WHEN 18 THEN
                SET condition_val = 'Sinusitis Acute';
                SET treatment_val = 'Decongestants and saline rinse recommended';
            ELSE
                SET condition_val = 'Preventive Care Visit';
                SET treatment_val = 'Health screenings and immunizations updated';
        END CASE;

        INSERT IGNORE INTO medical_records (record_id, patient_id, health_condition, treatment, date)
        VALUES (record_id_val, 9990100, condition_val, treatment_val, record_date);

        SET record_id_val = record_id_val + 1;
        SET i = i + 1;
    END WHILE;
END//

DELIMITER ;

CALL GenerateMediumMedicalRecords();
DROP PROCEDURE IF EXISTS GenerateMediumMedicalRecords;

-- ============================================================================
-- SECTION 4: Medical Records for TEST0001000 (Large - 1,000 records)
-- ============================================================================
DELIMITER //

DROP PROCEDURE IF EXISTS GenerateLargeMedicalRecords//

CREATE PROCEDURE GenerateLargeMedicalRecords()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE record_id_val INT DEFAULT 9900111;
    DECLARE condition_val VARCHAR(255);
    DECLARE treatment_val VARCHAR(255);
    DECLARE record_date DATE;
    DECLARE condition_index INT;

    WHILE i <= 1000 DO
        SET record_date = DATE_ADD('2020-01-01', INTERVAL (i % 1460) DAY);
        SET condition_index = i % 40;

        CASE condition_index
            WHEN 0 THEN SET condition_val = 'Annual Wellness Visit'; SET treatment_val = 'Comprehensive health evaluation completed';
            WHEN 1 THEN SET condition_val = 'Hypertension Stage 1'; SET treatment_val = 'Lifestyle modifications and ACE inhibitor started';
            WHEN 2 THEN SET condition_val = 'Type 2 Diabetes Mellitus'; SET treatment_val = 'Metformin 500mg twice daily initiated';
            WHEN 3 THEN SET condition_val = 'Chronic Lower Back Pain'; SET treatment_val = 'Physical therapy 2x weekly, muscle relaxants PRN';
            WHEN 4 THEN SET condition_val = 'Tension Headaches'; SET treatment_val = 'Stress management, OTC analgesics as needed';
            WHEN 5 THEN SET condition_val = 'Generalized Anxiety Disorder'; SET treatment_val = 'Sertraline 50mg daily, therapy referral';
            WHEN 6 THEN SET condition_val = 'Primary Insomnia'; SET treatment_val = 'Sleep study ordered, CBT-I recommended';
            WHEN 7 THEN SET condition_val = 'GERD with Esophagitis'; SET treatment_val = 'Omeprazole 20mg daily, dietary changes';
            WHEN 8 THEN SET condition_val = 'Influenza A'; SET treatment_val = 'Tamiflu 75mg twice daily for 5 days';
            WHEN 9 THEN SET condition_val = 'Complicated UTI'; SET treatment_val = 'Ciprofloxacin 500mg twice daily for 10 days';
            WHEN 10 THEN SET condition_val = 'Atopic Dermatitis'; SET treatment_val = 'Triamcinolone cream, moisturizer regimen';
            WHEN 11 THEN SET condition_val = 'Perennial Allergic Rhinitis'; SET treatment_val = 'Fluticasone nasal spray, antihistamine PRN';
            WHEN 12 THEN SET condition_val = 'Moderate Persistent Asthma'; SET treatment_val = 'Flovent HFA, albuterol rescue inhaler';
            WHEN 13 THEN SET condition_val = 'Hashimoto Thyroiditis'; SET treatment_val = 'Synthroid 75mcg daily, TSH monitoring';
            WHEN 14 THEN SET condition_val = 'Iron Deficiency Anemia'; SET treatment_val = 'Ferrous sulfate 325mg daily with vitamin C';
            WHEN 15 THEN SET condition_val = 'Osteopenia Lumbar Spine'; SET treatment_val = 'Calcium 1200mg + Vitamin D 2000 IU daily';
            WHEN 16 THEN SET condition_val = 'Major Depressive Disorder'; SET treatment_val = 'Lexapro 10mg daily, weekly therapy sessions';
            WHEN 17 THEN SET condition_val = 'Bilateral Carpal Tunnel'; SET treatment_val = 'Night splints, considering corticosteroid injection';
            WHEN 18 THEN SET condition_val = 'Chronic Sinusitis'; SET treatment_val = 'Nasal irrigation, referral to ENT specialist';
            WHEN 19 THEN SET condition_val = 'Preventive Health Maintenance'; SET treatment_val = 'Age-appropriate screenings ordered';
            WHEN 20 THEN SET condition_val = 'Hyperlipidemia'; SET treatment_val = 'Atorvastatin 20mg daily, diet counseling';
            WHEN 21 THEN SET condition_val = 'Obesity Class I'; SET treatment_val = 'Nutrition referral, exercise prescription';
            WHEN 22 THEN SET condition_val = 'Prediabetes'; SET treatment_val = 'Metformin 500mg daily, lifestyle intervention';
            WHEN 23 THEN SET condition_val = 'Plantar Fasciitis'; SET treatment_val = 'Stretching exercises, supportive footwear';
            WHEN 24 THEN SET condition_val = 'Rotator Cuff Tendinitis'; SET treatment_val = 'Physical therapy, NSAIDs as needed';
            WHEN 25 THEN SET condition_val = 'Tennis Elbow'; SET treatment_val = 'Brace, activity modification, ice therapy';
            WHEN 26 THEN SET condition_val = 'Knee Osteoarthritis'; SET treatment_val = 'Glucosamine supplement, low-impact exercise';
            WHEN 27 THEN SET condition_val = 'Vertigo BPPV'; SET treatment_val = 'Epley maneuver performed, meclizine PRN';
            WHEN 28 THEN SET condition_val = 'Tinnitus'; SET treatment_val = 'Hearing evaluation, white noise therapy';
            WHEN 29 THEN SET condition_val = 'Conjunctivitis Viral'; SET treatment_val = 'Artificial tears, cold compresses';
            WHEN 30 THEN SET condition_val = 'Contact Dermatitis'; SET treatment_val = 'Allergen avoidance, hydrocortisone cream';
            WHEN 31 THEN SET condition_val = 'Bronchitis Acute'; SET treatment_val = 'Supportive care, cough suppressant';
            WHEN 32 THEN SET condition_val = 'Strep Pharyngitis'; SET treatment_val = 'Amoxicillin 500mg three times daily for 10 days';
            WHEN 33 THEN SET condition_val = 'Otitis Media'; SET treatment_val = 'Amoxicillin-clavulanate, pain management';
            WHEN 34 THEN SET condition_val = 'Constipation Chronic'; SET treatment_val = 'Fiber supplement, increased water intake';
            WHEN 35 THEN SET condition_val = 'Hemorrhoids Internal'; SET treatment_val = 'Sitz baths, topical preparation H';
            WHEN 36 THEN SET condition_val = 'Vitamin B12 Deficiency'; SET treatment_val = 'B12 injections monthly';
            WHEN 37 THEN SET condition_val = 'Sleep Apnea Obstructive'; SET treatment_val = 'CPAP therapy initiated';
            WHEN 38 THEN SET condition_val = 'Restless Leg Syndrome'; SET treatment_val = 'Gabapentin 300mg at bedtime';
            ELSE SET condition_val = 'General Follow-up Visit'; SET treatment_val = 'Patient stable, continue current management';
        END CASE;

        INSERT IGNORE INTO medical_records (record_id, patient_id, health_condition, treatment, date)
        VALUES (record_id_val, 9991000, condition_val, treatment_val, record_date);

        SET record_id_val = record_id_val + 1;
        SET i = i + 1;
    END WHILE;
END//

DELIMITER ;

CALL GenerateLargeMedicalRecords();
DROP PROCEDURE IF EXISTS GenerateLargeMedicalRecords;

-- ============================================================================
-- SECTION 5: Medical Records for TEST0010000 (XLarge - 10,000 records)
-- ============================================================================
DELIMITER //

DROP PROCEDURE IF EXISTS GenerateXLargeMedicalRecords//

CREATE PROCEDURE GenerateXLargeMedicalRecords()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE record_id_val INT DEFAULT 9901111;
    DECLARE condition_val VARCHAR(255);
    DECLARE treatment_val VARCHAR(255);
    DECLARE record_date DATE;
    DECLARE condition_index INT;
    DECLARE batch_count INT DEFAULT 0;

    WHILE i <= 10000 DO
        SET record_date = DATE_ADD('2015-01-01', INTERVAL (i % 3650) DAY);
        SET condition_index = i % 50;

        CASE condition_index
            WHEN 0 THEN SET condition_val = 'Comprehensive Annual Physical'; SET treatment_val = 'Full health assessment with all screenings completed';
            WHEN 1 THEN SET condition_val = 'Essential Hypertension'; SET treatment_val = 'Lisinopril 10mg daily, lifestyle counseling';
            WHEN 2 THEN SET condition_val = 'Type 2 Diabetes Management'; SET treatment_val = 'A1C 7.2%, continue current regimen';
            WHEN 3 THEN SET condition_val = 'Lumbar Radiculopathy'; SET treatment_val = 'MRI ordered, gabapentin for nerve pain';
            WHEN 4 THEN SET condition_val = 'Migraine with Aura'; SET treatment_val = 'Sumatriptan 50mg PRN, propranolol prophylaxis';
            WHEN 5 THEN SET condition_val = 'Panic Disorder'; SET treatment_val = 'Alprazolam 0.5mg PRN, CBT therapy';
            WHEN 6 THEN SET condition_val = 'Chronic Insomnia'; SET treatment_val = 'Trazodone 50mg at bedtime';
            WHEN 7 THEN SET condition_val = 'Barrett Esophagus'; SET treatment_val = 'High-dose PPI, surveillance endoscopy';
            WHEN 8 THEN SET condition_val = 'COVID-19 Infection'; SET treatment_val = 'Paxlovid prescribed, isolation advised';
            WHEN 9 THEN SET condition_val = 'Pyelonephritis'; SET treatment_val = 'IV antibiotics then oral, urology referral';
            WHEN 10 THEN SET condition_val = 'Psoriasis Plaque Type'; SET treatment_val = 'Topical steroids, UV phototherapy';
            WHEN 11 THEN SET condition_val = 'Allergic Asthma'; SET treatment_val = 'Symbicort maintenance, allergy immunotherapy';
            WHEN 12 THEN SET condition_val = 'Severe Persistent Asthma'; SET treatment_val = 'Biologics considered, pulmonology referral';
            WHEN 13 THEN SET condition_val = 'Graves Disease'; SET treatment_val = 'Methimazole, beta-blocker for symptoms';
            WHEN 14 THEN SET condition_val = 'Thalassemia Minor'; SET treatment_val = 'Genetic counseling, folic acid supplement';
            WHEN 15 THEN SET condition_val = 'Osteoporosis Hip'; SET treatment_val = 'Bisphosphonate therapy initiated';
            WHEN 16 THEN SET condition_val = 'Bipolar II Disorder'; SET treatment_val = 'Lamotrigine 100mg daily, mood monitoring';
            WHEN 17 THEN SET condition_val = 'Cubital Tunnel Syndrome'; SET treatment_val = 'Elbow splint, surgical consultation';
            WHEN 18 THEN SET condition_val = 'Nasal Polyps'; SET treatment_val = 'Steroid nasal spray, ENT follow-up';
            WHEN 19 THEN SET condition_val = 'Health Maintenance Male 50+'; SET treatment_val = 'PSA, colonoscopy, lipid panel ordered';
            WHEN 20 THEN SET condition_val = 'Familial Hypercholesterolemia'; SET treatment_val = 'High-intensity statin, PCSK9 inhibitor';
            WHEN 21 THEN SET condition_val = 'Morbid Obesity'; SET treatment_val = 'Bariatric surgery evaluation';
            WHEN 22 THEN SET condition_val = 'Metabolic Syndrome'; SET treatment_val = 'Aggressive lifestyle intervention';
            WHEN 23 THEN SET condition_val = 'Achilles Tendinopathy'; SET treatment_val = 'Eccentric exercises, heel lifts';
            WHEN 24 THEN SET condition_val = 'Frozen Shoulder'; SET treatment_val = 'Physical therapy, corticosteroid injection';
            WHEN 25 THEN SET condition_val = 'De Quervain Tenosynovitis'; SET treatment_val = 'Thumb spica splint, steroid injection';
            WHEN 26 THEN SET condition_val = 'Hip Osteoarthritis'; SET treatment_val = 'Joint replacement evaluation';
            WHEN 27 THEN SET condition_val = 'Meniere Disease'; SET treatment_val = 'Low sodium diet, diuretic therapy';
            WHEN 28 THEN SET condition_val = 'Sudden Hearing Loss'; SET treatment_val = 'Oral steroids, audiometry follow-up';
            WHEN 29 THEN SET condition_val = 'Dry Eye Syndrome'; SET treatment_val = 'Restasis drops, warm compresses';
            WHEN 30 THEN SET condition_val = 'Seborrheic Dermatitis'; SET treatment_val = 'Ketoconazole shampoo, low-potency steroid';
            WHEN 31 THEN SET condition_val = 'Pneumonia Community Acquired'; SET treatment_val = 'Azithromycin Z-pack, chest X-ray follow-up';
            WHEN 32 THEN SET condition_val = 'Mononucleosis'; SET treatment_val = 'Supportive care, avoid contact sports';
            WHEN 33 THEN SET condition_val = 'Mastoiditis'; SET treatment_val = 'IV antibiotics, CT imaging';
            WHEN 34 THEN SET condition_val = 'IBS Mixed Type'; SET treatment_val = 'Low FODMAP diet, antispasmodic PRN';
            WHEN 35 THEN SET condition_val = 'Anal Fissure'; SET treatment_val = 'Fiber, topical nifedipine cream';
            WHEN 36 THEN SET condition_val = 'Pernicious Anemia'; SET treatment_val = 'B12 injections weekly initially';
            WHEN 37 THEN SET condition_val = 'Central Sleep Apnea'; SET treatment_val = 'ASV therapy, cardiology evaluation';
            WHEN 38 THEN SET condition_val = 'Periodic Limb Movement'; SET treatment_val = 'Iron studies, dopamine agonist';
            WHEN 39 THEN SET condition_val = 'Gout Acute Flare'; SET treatment_val = 'Colchicine, prednisone taper';
            WHEN 40 THEN SET condition_val = 'Rheumatoid Arthritis'; SET treatment_val = 'Methotrexate, rheumatology management';
            WHEN 41 THEN SET condition_val = 'Fibromyalgia'; SET treatment_val = 'Duloxetine, exercise program';
            WHEN 42 THEN SET condition_val = 'Chronic Fatigue Syndrome'; SET treatment_val = 'Graded exercise therapy, sleep optimization';
            WHEN 43 THEN SET condition_val = 'Erectile Dysfunction'; SET treatment_val = 'PDE5 inhibitor prescribed, cardiovascular screening';
            WHEN 44 THEN SET condition_val = 'Benign Prostatic Hyperplasia'; SET treatment_val = 'Tamsulosin 0.4mg daily';
            WHEN 45 THEN SET condition_val = 'Polycystic Ovary Syndrome'; SET treatment_val = 'Metformin, hormonal contraception';
            WHEN 46 THEN SET condition_val = 'Endometriosis'; SET treatment_val = 'Hormonal suppression therapy';
            WHEN 47 THEN SET condition_val = 'Peripheral Neuropathy'; SET treatment_val = 'Gabapentin titration, diabetes control';
            WHEN 48 THEN SET condition_val = 'Essential Tremor'; SET treatment_val = 'Propranolol trial';
            ELSE SET condition_val = 'Routine Follow-up Appointment'; SET treatment_val = 'Patient doing well, continue current plan';
        END CASE;

        INSERT IGNORE INTO medical_records (record_id, patient_id, health_condition, treatment, date)
        VALUES (record_id_val, 9910000, condition_val, treatment_val, record_date);

        SET record_id_val = record_id_val + 1;
        SET i = i + 1;

        -- Commit every 1000 records for performance
        SET batch_count = batch_count + 1;
        IF batch_count >= 1000 THEN
            SET batch_count = 0;
        END IF;
    END WHILE;
END//

DELIMITER ;

CALL GenerateXLargeMedicalRecords();
DROP PROCEDURE IF EXISTS GenerateXLargeMedicalRecords;

-- ============================================================================
-- Verification Queries
-- ============================================================================
-- Run these to verify the data was inserted correctly:
-- SELECT tax_identifier, given_name, family_name FROM patients WHERE tax_identifier LIKE 'TEST%';
-- SELECT p.tax_identifier, COUNT(*) as record_count
--   FROM patients p JOIN medical_records mr ON p.patient_id = mr.patient_id
--   WHERE p.tax_identifier LIKE 'TEST%' GROUP BY p.tax_identifier;
