-- =====================================================================
--  CIS6003 Advanced Programming - WRIT1
--  Database verification script
--
--  Six checks that prove the data-tier business rules actually fire.
--  Run with:   mysql -u root -p --force < 06_verify.sql
--  (--force is required: three of these checks are SUPPOSED to error.)
--
--  Screenshot the output of this script for the Testing section - it is
--  direct evidence that the trigger and the stored procedure enforce
--  business rules, which is the line the 70-100 band asks for.
--  Author      : Thanujaya Hasaranga Perera
--  Reg. number : st20374257
-- =====================================================================

USE sunrise_clinic;

SELECT '=== CHECK 1: a clashing booking must be REJECTED by the trigger ===' AS ' ';
-- Dr. Perera already has 14:00-14:30 booked two days from now (APT-...-000007).
-- 14:15-14:45 overlaps it, so trg_appointment_no_overlap must raise SQLSTATE 45000.
INSERT INTO appointment (patient_id, dentist_id, appointment_date, start_time, end_time, created_by)
SELECT (SELECT patient_id FROM patient WHERE contact_number = '0712345678'),
       (SELECT dentist_id FROM dentist WHERE registration_no = 'SLMC-14872'),
       DATE_ADD(CURDATE(), INTERVAL 2 DAY), '14:15:00', '14:45:00',
       (SELECT staff_id FROM staff WHERE username = 'rmenaka');

SELECT '=== CHECK 2: the 10-minute buffer must also be enforced ===' AS ' ';
-- 14:35 does not overlap 14:00-14:30, but it falls inside the 10-minute
-- turnaround buffer, so it must ALSO be rejected.
INSERT INTO appointment (patient_id, dentist_id, appointment_date, start_time, end_time, created_by)
SELECT (SELECT patient_id FROM patient WHERE contact_number = '0712345678'),
       (SELECT dentist_id FROM dentist WHERE registration_no = 'SLMC-14872'),
       DATE_ADD(CURDATE(), INTERVAL 2 DAY), '14:35:00', '15:05:00',
       (SELECT staff_id FROM staff WHERE username = 'rmenaka');

SELECT '=== CHECK 3: a non-clashing booking must SUCCEED ===' AS ' ';
-- 14:45 clears 14:30 + 10 minutes, so this one is legal.
INSERT INTO appointment (patient_id, dentist_id, appointment_date, start_time, end_time, created_by)
SELECT (SELECT patient_id FROM patient WHERE contact_number = '0712345678'),
       (SELECT dentist_id FROM dentist WHERE registration_no = 'SLMC-14872'),
       DATE_ADD(CURDATE(), INTERVAL 2 DAY), '14:45:00', '15:15:00',
       (SELECT staff_id FROM staff WHERE username = 'rmenaka');

SELECT appointment_no, appointment_date, start_time, end_time, status
  FROM appointment
 WHERE appointment_date = DATE_ADD(CURDATE(), INTERVAL 2 DAY)
 ORDER BY start_time;

SELECT '=== CHECK 4: billing a COMPLETED appointment must produce the right total ===' AS ' ';
-- Kamal Jayasuriya, senior citizen: RCT 25,000 + X-ray 3,000 = 28,000 subtotal.
-- 10% senior discount on treatments only = 2,800.
-- Dr. Perera consultation fee 3,000.
-- Expected total payable = 3,000 + 28,000 - 2,800 = 28,200.00
SET @appt = (SELECT a.appointment_no FROM appointment a
              JOIN patient p ON p.patient_id = a.patient_id
             WHERE p.contact_number = '0712345678' AND a.status = 'COMPLETED');

CALL sp_generate_bill(@appt, 2800.00, 'Senior citizen 10%', @bill_no);

SELECT @bill_no                AS bill_no,
       consultation_fee,
       treatment_subtotal,
       discount_amount,
       total_payable,
       CASE WHEN total_payable = 28200.00 THEN 'PASS' ELSE 'FAIL' END AS expected_28200
  FROM bill WHERE bill_no = @bill_no;

SELECT line_no, description, unit_price, quantity, line_total
  FROM bill_line
  JOIN bill USING (bill_id)
 WHERE bill.bill_no = @bill_no
 ORDER BY line_no;

SELECT '=== CHECK 5: the same appointment must not be billed twice ===' AS ' ';
CALL sp_generate_bill(@appt, 0.00, 'No discount', @bill_no2);

SELECT '=== CHECK 6: a BOOKED appointment must not be billable ===' AS ' ';
SET @future = (SELECT appointment_no FROM appointment WHERE status = 'BOOKED' LIMIT 1);
CALL sp_generate_bill(@future, 0.00, 'No discount', @bill_no3);

SELECT '=== Reports ===' AS ' ';
SELECT * FROM vw_daily_revenue;
SELECT * FROM vw_treatment_mix ORDER BY gross_income DESC;
SELECT * FROM vw_attendance_rate;
