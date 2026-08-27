-- =====================================================================
--  Database health check - run this and paste the output.
--  Every row must say PASS.
-- =====================================================================
USE sunrise_clinic;

SELECT 'Tables (expect 12)' AS check_name,
       COUNT(*) AS found,
       IF(COUNT(*) = 12, 'PASS', 'FAIL') AS result
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'sunrise_clinic' AND TABLE_TYPE = 'BASE TABLE'
UNION ALL
SELECT 'Views (expect 5)', COUNT(*), IF(COUNT(*) = 5, 'PASS', 'FAIL')
  FROM information_schema.VIEWS WHERE TABLE_SCHEMA = 'sunrise_clinic'
UNION ALL
SELECT 'Triggers (expect 4)', COUNT(*), IF(COUNT(*) = 4, 'PASS', 'FAIL')
  FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = 'sunrise_clinic'
UNION ALL
SELECT 'Function + procedure (expect 2)', COUNT(*), IF(COUNT(*) = 2, 'PASS', 'FAIL')
  FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA = 'sunrise_clinic'
UNION ALL
SELECT 'Foreign keys (expect 9)', COUNT(*), IF(COUNT(*) = 9, 'PASS', 'FAIL')
  FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = 'sunrise_clinic'
UNION ALL
SELECT 'Staff seeded (expect 5)', COUNT(*), IF(COUNT(*) = 5, 'PASS', 'FAIL') FROM staff
UNION ALL
SELECT 'Dentists seeded (expect 3)', COUNT(*), IF(COUNT(*) = 3, 'PASS', 'FAIL') FROM dentist
UNION ALL
SELECT 'Treatments seeded (expect 10)', COUNT(*), IF(COUNT(*) = 10, 'PASS', 'FAIL') FROM treatment_type
UNION ALL
SELECT 'Patients seeded (expect 5)', COUNT(*), IF(COUNT(*) = 5, 'PASS', 'FAIL') FROM patient
UNION ALL
SELECT 'Appointments seeded (expect 10+)', COUNT(*), IF(COUNT(*) >= 10, 'PASS', 'FAIL') FROM appointment
UNION ALL
SELECT 'Appointment numbers generated', COUNT(*), IF(COUNT(*) = 0, 'PASS', 'FAIL')
  FROM appointment WHERE appointment_no IS NULL
UNION ALL
SELECT 'Patient numbers generated', COUNT(*), IF(COUNT(*) = 0, 'PASS', 'FAIL')
  FROM patient WHERE patient_no IS NULL
UNION ALL
SELECT 'fn_treatment_subtotal returns 28000',
       fn_treatment_subtotal((SELECT a.appointment_id FROM appointment a
                               JOIN patient p ON p.patient_id = a.patient_id
                              WHERE p.contact_number = '0712345678' AND a.status = 'COMPLETED')),
       IF(fn_treatment_subtotal((SELECT a.appointment_id FROM appointment a
                                  JOIN patient p ON p.patient_id = a.patient_id
                                 WHERE p.contact_number = '0712345678' AND a.status = 'COMPLETED')) = 28000.00,
          'PASS', 'FAIL')
UNION ALL
SELECT 'BCrypt hashes stored (expect 5)', COUNT(*), IF(COUNT(*) = 5, 'PASS', 'FAIL')
  FROM staff WHERE password_hash LIKE '$2%$%';
