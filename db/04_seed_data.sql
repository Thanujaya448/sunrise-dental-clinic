-- =====================================================================
--  CIS6003 Advanced Programming - WRIT1
--  Script 4 of 5 : Reference and seed data
--
--  Passwords below are real BCrypt hashes (cost 10) of:
--      admin      / Admin@123
--      rmenaka    / Recep@123
--      dperera    / Dentist@123
--      dfernando  / Dentist@123
--      dsilva     / Dentist@123
--
--  These are development credentials only and are documented here so the
--  system can be demonstrated. The plaintext is never stored (FR-02).
--  Author: <your name / student ID>
-- =====================================================================

USE sunrise_clinic;

-- ---------------------------------------------------------------------
--  Clinic settings  -  ASM-06, ASM-08
-- ---------------------------------------------------------------------
INSERT INTO clinic_setting (setting_key, setting_value, description) VALUES
  ('OPENING_TIME',    '08:00', 'Earliest appointment start time'),
  ('CLOSING_TIME',    '20:00', 'Latest appointment end time'),
  ('CLOSED_WEEKDAY',  '7',     'ISO day the clinic is closed (7 = Sunday)'),
  ('BUFFER_MINUTES',  '10',    'Turnaround gap required between a dentist''s appointments'),
  ('SLOT_GRANULARITY','15',    'Appointments start on this minute grid'),
  ('MAX_LOGIN_FAILS', '5',     'Failed attempts before the account locks (FR-03)'),
  ('SESSION_MINUTES', '20',    'Idle minutes before a session expires (FR-04)');

-- ---------------------------------------------------------------------
--  Staff  -  one of each role, plus three dentists
-- ---------------------------------------------------------------------
INSERT INTO staff (username, password_hash, full_name, role) VALUES
  ('admin',     '$2b$10$DcjSigstwtCZDPmZIqvYO.iVTcvQujc1moQkfC9Ll3dmHwbtMHG5u', 'Nadeesha Wickramasinghe', 'ADMINISTRATOR'),
  ('rmenaka',   '$2b$10$XxU56PJDs6ECvQhWfnssveYszvVyoGJD6jYd38ugrrg8JAjQuqORW', 'Menaka Rajapaksha',       'RECEPTIONIST'),
  ('dperera',   '$2b$10$0Cd1SegY1xj3j8yR.C3WjevwkUSsR61zAqip1RVWBHtWSD20wGfS6', 'Dr. Anushka Perera',      'DENTIST'),
  ('dfernando', '$2b$10$MOykx4ay/GV.IEA1QvbbfORorAusHpHQSoI3mtWhi6VaiGx.dVURy', 'Dr. Roshan Fernando',     'DENTIST'),
  ('dsilva',    '$2b$10$IcHv1byEAcKlzdwH6K9GKuA4xunFCRT6hu01NHIuPRavu1gXqYaKa', 'Dr. Ishara Silva',        'DENTIST');

INSERT INTO receptionist (staff_id, desk_number)
SELECT staff_id, 'D-01' FROM staff WHERE username = 'rmenaka';

INSERT INTO dentist (staff_id, registration_no, specialisation, consultation_fee)
SELECT staff_id, 'SLMC-14872', 'Restorative Dentistry', 3000.00 FROM staff WHERE username = 'dperera';
INSERT INTO dentist (staff_id, registration_no, specialisation, consultation_fee)
SELECT staff_id, 'SLMC-15330', 'General Dentistry',     2000.00 FROM staff WHERE username = 'dfernando';
INSERT INTO dentist (staff_id, registration_no, specialisation, consultation_fee)
SELECT staff_id, 'SLMC-16104', 'Orthodontics',          2500.00 FROM staff WHERE username = 'dsilva';

-- ---------------------------------------------------------------------
--  Treatment types  -  prices in LKR (ASM-11), durations (ASM-07)
-- ---------------------------------------------------------------------
INSERT INTO treatment_type (code, name, price, duration_minutes) VALUES
  ('CONS', 'Consultation / check-up',  2000.00, 15),
  ('XRAY', 'Dental X-ray',             3000.00, 15),
  ('EXTR', 'Extraction (simple)',      4500.00, 30),
  ('FILL', 'Filling (composite)',      5500.00, 30),
  ('SCAL', 'Scaling and polishing',    6500.00, 30),
  ('ORTH', 'Orthodontic adjustment',   7500.00, 30),
  ('SEXT', 'Surgical extraction',     12000.00, 60),
  ('RCT',  'Root canal treatment',    25000.00, 90),
  ('CRWN', 'Crown fitting',           35000.00, 60),
  ('DENT', 'Partial denture',         45000.00, 60);

-- ---------------------------------------------------------------------
--  Patients  -  deliberately chosen to exercise every discount rule
--    Kamal      born 1955  -> senior citizen (10%)
--    Ayesha     staff family flag        -> staff discount (15%)
--    Nimal      five completed visits    -> loyalty (5%)
--    Tharindu   none of the above        -> no discount
-- ---------------------------------------------------------------------
INSERT INTO patient (full_name, address, contact_number, email, date_of_birth, is_staff_family) VALUES
  ('Kamal Jayasuriya',  '14/3 Temple Road, Nugegoda',   '0712345678', 'kamal.j@example.lk',    '1955-04-12', FALSE),
  ('Ayesha Mendis',     '221 Galle Road, Dehiwala',     '0776543210', 'ayesha.m@example.lk',   '1992-11-30', TRUE),
  ('Nimal Bandara',     '8 Lake Drive, Battaramulla',   '0701122334', 'nimal.b@example.lk',    '1978-06-05', FALSE),
  ('Tharindu Weerasing','52 Station Road, Maharagama',  '0759988776', NULL,                    '2001-02-18', FALSE),
  ('Shalini Gunaratne', '3B Flower Avenue, Colombo 07', '0723344556', 'shalini.g@example.lk',  '1988-09-22', FALSE);

-- ---------------------------------------------------------------------
--  Appointments
--  Dates are relative to CURDATE() so the seed never goes stale.
--  Note APT numbers are assigned by trg_appointment_number, not here.
-- ---------------------------------------------------------------------
SET @rec  = (SELECT staff_id   FROM staff   WHERE username = 'rmenaka');
SET @dr1  = (SELECT dentist_id FROM dentist WHERE registration_no = 'SLMC-14872');  -- Perera
SET @dr2  = (SELECT dentist_id FROM dentist WHERE registration_no = 'SLMC-15330');  -- Fernando
SET @dr3  = (SELECT dentist_id FROM dentist WHERE registration_no = 'SLMC-16104');  -- Silva

SET @p1 = (SELECT patient_id FROM patient WHERE contact_number = '0712345678');
SET @p2 = (SELECT patient_id FROM patient WHERE contact_number = '0776543210');
SET @p3 = (SELECT patient_id FROM patient WHERE contact_number = '0701122334');
SET @p4 = (SELECT patient_id FROM patient WHERE contact_number = '0759988776');
SET @p5 = (SELECT patient_id FROM patient WHERE contact_number = '0723344556');

-- Four completed visits for Nimal so that the fifth earns loyalty (ASM-12)
INSERT INTO appointment (patient_id, dentist_id, appointment_date, start_time, end_time, status, created_by) VALUES
  (@p3, @dr2, DATE_SUB(CURDATE(), INTERVAL 120 DAY), '09:00:00', '09:15:00', 'COMPLETED', @rec),
  (@p3, @dr2, DATE_SUB(CURDATE(), INTERVAL  90 DAY), '09:00:00', '09:30:00', 'COMPLETED', @rec),
  (@p3, @dr2, DATE_SUB(CURDATE(), INTERVAL  60 DAY), '10:00:00', '10:30:00', 'COMPLETED', @rec),
  (@p3, @dr2, DATE_SUB(CURDATE(), INTERVAL  30 DAY), '11:00:00', '11:30:00', 'COMPLETED', @rec);

-- A completed visit ready to be billed  (senior citizen, RCT + X-ray)
INSERT INTO appointment (patient_id, dentist_id, appointment_date, start_time, end_time, status, created_by, notes)
VALUES (@p1, @dr1, DATE_SUB(CURDATE(), INTERVAL 1 DAY), '10:00:00', '11:45:00', 'COMPLETED', @rec,
        'Root canal on upper left molar, X-ray taken first');

-- Future bookings
INSERT INTO appointment (patient_id, dentist_id, appointment_date, start_time, end_time, status, created_by) VALUES
  (@p2, @dr3, DATE_ADD(CURDATE(), INTERVAL 2 DAY), '09:00:00', '09:30:00', 'BOOKED', @rec),
  (@p4, @dr1, DATE_ADD(CURDATE(), INTERVAL 2 DAY), '14:00:00', '14:30:00', 'BOOKED', @rec),
  (@p5, @dr2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), '11:00:00', '12:00:00', 'BOOKED', @rec);

-- A cancellation and a no-show, so the reports have something to report
INSERT INTO appointment (patient_id, dentist_id, appointment_date, start_time, end_time, status, created_by, cancel_reason) VALUES
  (@p5, @dr1, DATE_SUB(CURDATE(), INTERVAL 7 DAY), '15:00:00', '15:30:00', 'CANCELLED', @rec, 'Patient rescheduled by phone');
INSERT INTO appointment (patient_id, dentist_id, appointment_date, start_time, end_time, status, created_by) VALUES
  (@p4, @dr2, DATE_SUB(CURDATE(), INTERVAL 5 DAY), '16:00:00', '16:30:00', 'NO_SHOW', @rec);

-- ---------------------------------------------------------------------
--  Treatments on each appointment  (unit_price snapshots today's price)
-- ---------------------------------------------------------------------
INSERT INTO appointment_treatment (appointment_id, treatment_id, quantity, unit_price)
SELECT a.appointment_id, t.treatment_id, 1, t.price
  FROM appointment a
  JOIN treatment_type t ON t.code IN ('RCT','XRAY')
 WHERE a.patient_id = @p1 AND a.status = 'COMPLETED';

INSERT INTO appointment_treatment (appointment_id, treatment_id, quantity, unit_price)
SELECT a.appointment_id, t.treatment_id, 1, t.price
  FROM appointment a
  JOIN treatment_type t ON t.code = 'SCAL'
 WHERE a.patient_id = @p3 AND a.status = 'COMPLETED';

INSERT INTO appointment_treatment (appointment_id, treatment_id, quantity, unit_price)
SELECT a.appointment_id, t.treatment_id, 1, t.price
  FROM appointment a
  JOIN treatment_type t ON t.code = 'ORTH'
 WHERE a.patient_id = @p2 AND a.status = 'BOOKED';

INSERT INTO appointment_treatment (appointment_id, treatment_id, quantity, unit_price)
SELECT a.appointment_id, t.treatment_id, 1, t.price
  FROM appointment a
  JOIN treatment_type t ON t.code = 'FILL'
 WHERE a.patient_id = @p4 AND a.status = 'BOOKED';

INSERT INTO appointment_treatment (appointment_id, treatment_id, quantity, unit_price)
SELECT a.appointment_id, t.treatment_id, 1, t.price
  FROM appointment a
  JOIN treatment_type t ON t.code = 'CRWN'
 WHERE a.patient_id = @p5 AND a.status = 'BOOKED';
