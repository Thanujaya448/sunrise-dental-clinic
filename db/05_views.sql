-- =====================================================================
--  CIS6003 Advanced Programming - WRIT1
--  Script 5 of 5 : Views behind the management reports
--
--  Rubric: "Proposed reports to facilitate decision-making". Each view
--  answers a question the clinic manager would actually act on, not
--  just a list of rows.
--
--  NOTE: every non-aggregated column appears in GROUP BY. MySQL 8 enables
--  ONLY_FULL_GROUP_BY by default and rejects views that select a column
--  which is neither aggregated nor grouped. Writing them this way is both
--  standard SQL and portable.
--  Author: <your name / student ID>
-- =====================================================================

USE sunrise_clinic;

DROP VIEW IF EXISTS vw_appointment_detail;
DROP VIEW IF EXISTS vw_daily_revenue;
DROP VIEW IF EXISTS vw_dentist_utilisation;
DROP VIEW IF EXISTS vw_treatment_mix;
DROP VIEW IF EXISTS vw_attendance_rate;

-- ---------------------------------------------------------------------
--  vw_appointment_detail   -  backs UC-10 Search & Display Appointment
--  One row per appointment with everything the receptionist needs, so
--  the search screen makes a single query instead of four.
-- ---------------------------------------------------------------------
CREATE VIEW vw_appointment_detail AS
SELECT a.appointment_no,
       a.appointment_date,
       a.start_time,
       a.end_time,
       a.status,
       p.patient_no,
       p.full_name      AS patient_name,
       p.address        AS patient_address,
       p.contact_number,
       ds.full_name     AS dentist_name,
       d.specialisation,
       d.consultation_fee,
       GROUP_CONCAT(CONCAT(t.code, ' ', t.name)
                    ORDER BY t.code SEPARATOR ', ') AS treatments,
       fn_treatment_subtotal(a.appointment_id)      AS treatment_subtotal,
       a.notes
  FROM appointment a
  JOIN patient p        ON p.patient_id  = a.patient_id
  JOIN dentist d        ON d.dentist_id  = a.dentist_id
  JOIN staff   ds       ON ds.staff_id   = d.staff_id
  LEFT JOIN appointment_treatment at ON at.appointment_id = a.appointment_id
  LEFT JOIN treatment_type t         ON t.treatment_id    = at.treatment_id
 GROUP BY a.appointment_id, a.appointment_no, a.appointment_date,
          a.start_time, a.end_time, a.status, a.notes,
          p.patient_no, p.full_name, p.address, p.contact_number,
          ds.full_name, d.specialisation, d.consultation_fee;

-- ---------------------------------------------------------------------
--  vw_daily_revenue
--  Decision it supports: is takings trending up or down, and which days
--  are worth opening for.
-- ---------------------------------------------------------------------
CREATE VIEW vw_daily_revenue AS
SELECT DATE(b.issued_on)              AS revenue_date,
       COUNT(*)                       AS bills_issued,
       SUM(b.consultation_fee)        AS consultation_income,
       SUM(b.treatment_subtotal)      AS treatment_income,
       SUM(b.discount_amount)         AS discounts_given,
       SUM(b.total_payable)           AS total_revenue,
       SUM(CASE WHEN b.payment_status = 'UNPAID'
                THEN b.total_payable ELSE 0 END) AS outstanding
  FROM bill b
 GROUP BY DATE(b.issued_on);

-- ---------------------------------------------------------------------
--  vw_dentist_utilisation
--  Decision it supports: who is overbooked and who has capacity.
--  Booked minutes are compared against a 12-hour working day.
-- ---------------------------------------------------------------------
CREATE VIEW vw_dentist_utilisation AS
SELECT s.full_name                      AS dentist_name,
       d.specialisation,
       a.appointment_date,
       COUNT(*)                         AS appointments,
       SUM(TIMESTAMPDIFF(MINUTE, a.start_time, a.end_time)) AS booked_minutes,
       ROUND(SUM(TIMESTAMPDIFF(MINUTE, a.start_time, a.end_time)) / 720 * 100, 1)
                                        AS utilisation_pct
  FROM appointment a
  JOIN dentist d ON d.dentist_id = a.dentist_id
  JOIN staff   s ON s.staff_id   = d.staff_id
 WHERE a.status IN ('BOOKED','COMPLETED')
 GROUP BY d.dentist_id, s.full_name, d.specialisation, a.appointment_date;

-- ---------------------------------------------------------------------
--  vw_treatment_mix
--  Decision it supports: which treatments earn the practice its money,
--  and therefore what to schedule capacity and stock for.
-- ---------------------------------------------------------------------
CREATE VIEW vw_treatment_mix AS
SELECT t.code,
       t.name,
       COUNT(*)                          AS times_performed,
       SUM(at.unit_price * at.quantity)  AS gross_income,
       ROUND(SUM(at.unit_price * at.quantity) * 100.0 /
             NULLIF((SELECT SUM(unit_price * quantity)
                       FROM appointment_treatment), 0), 1) AS income_share_pct
  FROM appointment_treatment at
  JOIN treatment_type t ON t.treatment_id = at.treatment_id
  JOIN appointment a    ON a.appointment_id = at.appointment_id
 WHERE a.status <> 'CANCELLED'
 GROUP BY t.treatment_id, t.code, t.name;

-- ---------------------------------------------------------------------
--  vw_attendance_rate
--  Decision it supports: whether reminders are working. A rising
--  no-show rate is the business case for the notification feature.
-- ---------------------------------------------------------------------
CREATE VIEW vw_attendance_rate AS
SELECT DATE_FORMAT(a.appointment_date, '%Y-%m')       AS period,
       COUNT(*)                                       AS total_appointments,
       SUM(a.status = 'COMPLETED')                    AS completed,
       SUM(a.status = 'CANCELLED')                    AS cancelled,
       SUM(a.status = 'NO_SHOW')                      AS no_shows,
       ROUND(SUM(a.status = 'NO_SHOW') * 100.0 / COUNT(*), 1) AS no_show_pct
  FROM appointment a
 WHERE a.appointment_date < CURDATE()
 GROUP BY DATE_FORMAT(a.appointment_date, '%Y-%m');
