-- =====================================================================
--  CIS6003 Advanced Programming - WRIT1
--  Script 3 of 5 : Stored function and stored procedure
--
--  fn_treatment_subtotal   set arithmetic, done where the data lives
--  sp_generate_bill        the whole bill written in one transaction
--  Author      : Thanujaya Hasaranga Perera
--  Reg. number : st20374257
-- =====================================================================

USE sunrise_clinic;

DROP FUNCTION  IF EXISTS fn_treatment_subtotal;
DROP PROCEDURE IF EXISTS sp_generate_bill;

DELIMITER $$

-- ---------------------------------------------------------------------
--  fn_treatment_subtotal
--
--  Sums the snapshot prices of every treatment on one appointment.
--  Doing this in SQL rather than Java means one value crosses the
--  network instead of every treatment row.
-- ---------------------------------------------------------------------
CREATE FUNCTION fn_treatment_subtotal(p_appointment_id BIGINT)
RETURNS DECIMAL(12,2)
READS SQL DATA
DETERMINISTIC
BEGIN
    DECLARE v_subtotal DECIMAL(12,2);

    SELECT COALESCE(SUM(at.unit_price * at.quantity), 0.00)
      INTO v_subtotal
      FROM appointment_treatment at
     WHERE at.appointment_id = p_appointment_id;

    RETURN v_subtotal;
END$$

-- ---------------------------------------------------------------------
--  sp_generate_bill
--
--  Business rules implemented here (rubric: "stored procedures ...
--  to implement business rules"):
--
--    1. Only a COMPLETED appointment can be billed        (FR-18, ASM-05)
--    2. An appointment may be billed once only            (uq_bill_appt)
--    3. total = consultation fee + treatments - discount  (ASM-10)
--    4. The discount applies to treatments only, never to
--       the consultation fee                              (ASM-12)
--    5. Header and lines are written together or not at all
--
--  The discount AMOUNT is calculated in Java by the Strategy pattern and
--  passed in. Choosing WHICH rule applies is business policy and belongs
--  in the service tier; summing and writing rows belongs here.
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_generate_bill(
    IN  p_appointment_no  VARCHAR(20),
    IN  p_discount_amount DECIMAL(10,2),
    IN  p_discount_label  VARCHAR(40),
    OUT p_bill_no         VARCHAR(20)
)
MODIFIES SQL DATA
BEGIN
    DECLARE v_appointment_id BIGINT;
    DECLARE v_status         VARCHAR(20);
    DECLARE v_dentist_name   VARCHAR(100);
    DECLARE v_consult_fee    DECIMAL(10,2);
    DECLARE v_subtotal       DECIMAL(12,2);
    DECLARE v_total          DECIMAL(12,2);
    DECLARE v_year           SMALLINT;
    DECLARE v_next           INT;
    DECLARE v_bill_id        BIGINT;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    -- ---- resolve the appointment ------------------------------------
    SELECT a.appointment_id, a.status, s.full_name, d.consultation_fee
      INTO v_appointment_id, v_status, v_dentist_name, v_consult_fee
      FROM appointment a
      JOIN dentist d ON d.dentist_id = a.dentist_id
      JOIN staff   s ON s.staff_id   = d.staff_id
     WHERE a.appointment_no = p_appointment_no;

    IF v_appointment_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
          SET MESSAGE_TEXT = 'No such appointment number';
    END IF;

    -- ---- rule 2 : an appointment may be billed once only ------------
    IF EXISTS (SELECT 1 FROM bill WHERE appointment_id = v_appointment_id) THEN
        SIGNAL SQLSTATE '45000'
          SET MESSAGE_TEXT = 'This appointment has already been billed';
    END IF;

    -- ---- rule 1 : only completed appointments are billable ----------
    IF v_status <> 'COMPLETED' THEN
        SIGNAL SQLSTATE '45000'
          SET MESSAGE_TEXT = 'Appointment is not completed, so it cannot be billed';
    END IF;

    -- ---- rules 3 and 4 : the arithmetic -----------------------------
    SET v_subtotal = fn_treatment_subtotal(v_appointment_id);

    IF p_discount_amount > v_subtotal THEN
        SIGNAL SQLSTATE '45000'
          SET MESSAGE_TEXT = 'Discount cannot exceed the treatment subtotal';
    END IF;

    SET v_total = v_consult_fee + v_subtotal - p_discount_amount;

    -- ---- bill number ------------------------------------------------
    SET v_year = YEAR(CURDATE());
    INSERT INTO number_sequence (seq_name, seq_year, last_issued)
         VALUES ('BILL', v_year, 1)
    ON DUPLICATE KEY UPDATE last_issued = last_issued + 1;

    SELECT last_issued INTO v_next
      FROM number_sequence
     WHERE seq_name = 'BILL' AND seq_year = v_year;

    SET p_bill_no = CONCAT('BIL-', v_year, '-', LPAD(v_next, 6, '0'));

    -- ---- rule 5 : header and lines together -------------------------
    INSERT INTO bill (bill_no, appointment_id, consultation_fee,
                      treatment_subtotal, discount_amount, discount_label,
                      total_payable, payment_status)
         VALUES (p_bill_no, v_appointment_id, v_consult_fee,
                 v_subtotal, p_discount_amount, p_discount_label,
                 v_total, 'UNPAID');

    SET v_bill_id = LAST_INSERT_ID();

    INSERT INTO bill_line (bill_id, line_no, description, unit_price, quantity)
         VALUES (v_bill_id, 1,
                 CONCAT('Consultation - ', v_dentist_name),
                 v_consult_fee, 1);

    INSERT INTO bill_line (bill_id, line_no, description, unit_price, quantity)
    SELECT v_bill_id,
           ROW_NUMBER() OVER (ORDER BY t.code) + 1,
           CONCAT(t.code, ' - ', t.name),
           at.unit_price,
           at.quantity
      FROM appointment_treatment at
      JOIN treatment_type t ON t.treatment_id = at.treatment_id
     WHERE at.appointment_id = v_appointment_id;

    INSERT INTO audit_entry (performed_by, action, entity_ref, detail)
         VALUES ('SYSTEM', 'BILL_GENERATED', p_bill_no,
                 CONCAT('Appointment ', p_appointment_no, ', total ', v_total));

    COMMIT;
END$$

DELIMITER ;
