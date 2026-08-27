-- =====================================================================
--  CIS6003 Advanced Programming - WRIT1
--  Script 2 of 5 : Triggers  (business rules enforced in the data tier)
--
--  Four triggers, each with one responsibility:
--    trg_patient_number          generates PAT-YYYY-NNNNNN
--    trg_appointment_number      generates APT-YYYY-NNNNNN
--    trg_appointment_no_overlap  rejects clashing INSERTs   <- headline rule
--    trg_appointment_overlap_upd rejects clashing UPDATEs   (reschedule)
--
--  FOLLOWS is used so the numbering trigger always runs before the
--  overlap check on the same INSERT. Splitting them keeps each trigger
--  single-purpose and testable.
--  Author: <your name / student ID>
-- =====================================================================

USE sunrise_clinic;

DROP TRIGGER IF EXISTS trg_patient_number;
DROP TRIGGER IF EXISTS trg_appointment_number;
DROP TRIGGER IF EXISTS trg_appointment_no_overlap;
DROP TRIGGER IF EXISTS trg_appointment_overlap_upd;

DELIMITER $$

-- ---------------------------------------------------------------------
--  trg_patient_number   -  ASM-04
-- ---------------------------------------------------------------------
CREATE TRIGGER trg_patient_number
BEFORE INSERT ON patient
FOR EACH ROW
BEGIN
    DECLARE v_year SMALLINT;
    DECLARE v_next INT;

    IF NEW.patient_no IS NULL THEN
        SET v_year = YEAR(CURDATE());

        INSERT INTO number_sequence (seq_name, seq_year, last_issued)
             VALUES ('PATIENT', v_year, 1)
        ON DUPLICATE KEY UPDATE last_issued = last_issued + 1;

        SELECT last_issued INTO v_next
          FROM number_sequence
         WHERE seq_name = 'PATIENT' AND seq_year = v_year;

        SET NEW.patient_no = CONCAT('PAT-', v_year, '-', LPAD(v_next, 6, '0'));
    END IF;
END$$

-- ---------------------------------------------------------------------
--  trg_appointment_number   -  ASM-04
--  Generated in the database, not in Java, so that two receptionists
--  booking simultaneously cannot be issued the same number.
-- ---------------------------------------------------------------------
CREATE TRIGGER trg_appointment_number
BEFORE INSERT ON appointment
FOR EACH ROW
BEGIN
    DECLARE v_year SMALLINT;
    DECLARE v_next INT;

    IF NEW.appointment_no IS NULL THEN
        SET v_year = YEAR(NEW.appointment_date);

        INSERT INTO number_sequence (seq_name, seq_year, last_issued)
             VALUES ('APPOINTMENT', v_year, 1)
        ON DUPLICATE KEY UPDATE last_issued = last_issued + 1;

        SELECT last_issued INTO v_next
          FROM number_sequence
         WHERE seq_name = 'APPOINTMENT' AND seq_year = v_year;

        SET NEW.appointment_no = CONCAT('APT-', v_year, '-', LPAD(v_next, 6, '0'));
    END IF;
END$$

-- ---------------------------------------------------------------------
--  trg_appointment_no_overlap   -  ASM-08, the clinic's core rule
--
--  Two appointments clash when their time ranges, each widened by the
--  turnaround buffer, intersect for the same dentist on the same day:
--
--      new.start < existing.end + buffer
--      AND new.end + buffer > existing.start
--
--  Cancelled and no-show appointments are ignored - they free the slot.
--
--  The service layer performs the same check before inserting. This
--  trigger exists because between that check and this INSERT another
--  transaction can commit the same slot. Only a rule inside the
--  transaction can close that window (NFR-05).
-- ---------------------------------------------------------------------
CREATE TRIGGER trg_appointment_no_overlap
BEFORE INSERT ON appointment
FOR EACH ROW FOLLOWS trg_appointment_number
BEGIN
    DECLARE v_buffer INT DEFAULT 10;
    DECLARE v_clashes INT DEFAULT 0;
    DECLARE v_msg VARCHAR(200);

    SELECT CAST(setting_value AS UNSIGNED) INTO v_buffer
      FROM clinic_setting
     WHERE setting_key = 'BUFFER_MINUTES';

    IF NEW.status = 'BOOKED' THEN
        SELECT COUNT(*) INTO v_clashes
          FROM appointment a
         WHERE a.dentist_id       = NEW.dentist_id
           AND a.appointment_date = NEW.appointment_date
           AND a.status           = 'BOOKED'
           AND a.start_time  < ADDTIME(NEW.end_time, SEC_TO_TIME(v_buffer * 60))
           AND ADDTIME(a.end_time, SEC_TO_TIME(v_buffer * 60)) > NEW.start_time;

        IF v_clashes > 0 THEN
            SET v_msg = CONCAT('Overlapping appointment: dentist ', NEW.dentist_id,
                               ' is already booked on ', NEW.appointment_date,
                               ' between ', NEW.start_time, ' and ', NEW.end_time);
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
        END IF;
    END IF;
END$$

-- ---------------------------------------------------------------------
--  trg_appointment_overlap_upd   -  the same rule on reschedule (FR-15)
--  The row being changed is excluded, otherwise it would clash with
--  itself.
-- ---------------------------------------------------------------------
CREATE TRIGGER trg_appointment_overlap_upd
BEFORE UPDATE ON appointment
FOR EACH ROW
BEGIN
    DECLARE v_buffer INT DEFAULT 10;
    DECLARE v_clashes INT DEFAULT 0;

    SELECT CAST(setting_value AS UNSIGNED) INTO v_buffer
      FROM clinic_setting
     WHERE setting_key = 'BUFFER_MINUTES';

    IF NEW.status = 'BOOKED'
       AND (NEW.appointment_date <> OLD.appointment_date
            OR NEW.start_time <> OLD.start_time
            OR NEW.end_time   <> OLD.end_time
            OR NEW.dentist_id <> OLD.dentist_id
            OR OLD.status     <> 'BOOKED') THEN

        SELECT COUNT(*) INTO v_clashes
          FROM appointment a
         WHERE a.dentist_id       = NEW.dentist_id
           AND a.appointment_date = NEW.appointment_date
           AND a.status           = 'BOOKED'
           AND a.appointment_id  <> NEW.appointment_id
           AND a.start_time  < ADDTIME(NEW.end_time, SEC_TO_TIME(v_buffer * 60))
           AND ADDTIME(a.end_time, SEC_TO_TIME(v_buffer * 60)) > NEW.start_time;

        IF v_clashes > 0 THEN
            SIGNAL SQLSTATE '45000'
              SET MESSAGE_TEXT = 'Overlapping appointment: that dentist is already booked at the new time';
        END IF;
    END IF;
END$$

DELIMITER ;
