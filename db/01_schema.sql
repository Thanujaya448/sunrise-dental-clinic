-- =====================================================================
--  CIS6003 Advanced Programming - WRIT1
--  Sunrise Dental Clinic Appointment & Patient Management System
--  Script 1 of 5 : Schema  (tables, keys, constraints, indexes)
--
--  Target      : MySQL 8.0 (InnoDB, utf8mb4)
--  Normal form : 3NF - justified in the report, section "Database design"
--  Author      : <your name / student ID>
-- =====================================================================

DROP DATABASE IF EXISTS sunrise_clinic;
CREATE DATABASE sunrise_clinic
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
USE sunrise_clinic;

-- ---------------------------------------------------------------------
--  clinic_setting
--  Operating hours and the turnaround buffer live in data, not in code
--  (ASM-06, ASM-08). The overlap trigger reads the buffer from here, so
--  changing it is a data change rather than a redeployment.
-- ---------------------------------------------------------------------
CREATE TABLE clinic_setting (
    setting_key    VARCHAR(40)  NOT NULL,
    setting_value  VARCHAR(40)  NOT NULL,
    description    VARCHAR(150) NOT NULL,
    CONSTRAINT pk_clinic_setting PRIMARY KEY (setting_key)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
--  number_sequence
--  Server-side generator for APT- / PAT- / BIL- reference numbers
--  (ASM-04). Held in the database rather than in Java so that two
--  receptionists on two machines cannot be issued the same number.
-- ---------------------------------------------------------------------
CREATE TABLE number_sequence (
    seq_name    VARCHAR(20) NOT NULL,
    seq_year    SMALLINT    NOT NULL,
    last_value  INT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_number_sequence PRIMARY KEY (seq_name, seq_year)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
--  staff  -  the StaffUser superclass
--  Inheritance is mapped table-per-subclass: shared attributes here,
--  role-specific attributes in dentist / receptionist.
-- ---------------------------------------------------------------------
CREATE TABLE staff (
    staff_id        BIGINT       NOT NULL AUTO_INCREMENT,
    username        VARCHAR(40)  NOT NULL,
    password_hash   VARCHAR(72)  NOT NULL,   -- BCrypt output, FR-02
    full_name       VARCHAR(100) NOT NULL,
    role            ENUM('RECEPTIONIST','DENTIST','ADMINISTRATOR') NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_attempts TINYINT      NOT NULL DEFAULT 0,   -- FR-03
    locked          BOOLEAN      NOT NULL DEFAULT FALSE,
    created_on      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_staff        PRIMARY KEY (staff_id),
    CONSTRAINT uq_staff_user   UNIQUE (username),
    CONSTRAINT ck_staff_att    CHECK (failed_attempts BETWEEN 0 AND 10)
) ENGINE=InnoDB;

CREATE TABLE receptionist (
    staff_id    BIGINT      NOT NULL,
    desk_number VARCHAR(10) NOT NULL,
    CONSTRAINT pk_receptionist PRIMARY KEY (staff_id),
    CONSTRAINT fk_recep_staff  FOREIGN KEY (staff_id) REFERENCES staff (staff_id)
        ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE dentist (
    dentist_id       BIGINT        NOT NULL AUTO_INCREMENT,
    staff_id         BIGINT        NOT NULL,
    registration_no  VARCHAR(20)   NOT NULL,
    specialisation   VARCHAR(60)   NOT NULL,
    consultation_fee DECIMAL(10,2) NOT NULL,   -- ASM-10, fee belongs to the dentist
    active           BOOLEAN       NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_dentist       PRIMARY KEY (dentist_id),
    CONSTRAINT uq_dentist_staff UNIQUE (staff_id),
    CONSTRAINT uq_dentist_reg   UNIQUE (registration_no),
    CONSTRAINT fk_dentist_staff FOREIGN KEY (staff_id) REFERENCES staff (staff_id)
        ON DELETE CASCADE,
    CONSTRAINT ck_dentist_fee   CHECK (consultation_fee >= 0)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
--  patient  -  persistent across visits (ASM-03)
--  Storing the patient once and referencing it is what prevents the
--  "lost patient records" failure described in the scenario.
-- ---------------------------------------------------------------------
CREATE TABLE patient (
    patient_id     BIGINT       NOT NULL AUTO_INCREMENT,
    patient_no     VARCHAR(20)  NOT NULL,
    full_name      VARCHAR(100) NOT NULL,
    address        VARCHAR(200) NOT NULL,
    contact_number VARCHAR(20)  NOT NULL,
    email          VARCHAR(120) NULL,
    date_of_birth  DATE         NOT NULL,
    is_staff_family BOOLEAN     NOT NULL DEFAULT FALSE,  -- ASM-12 discount input
    registered_on  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_patient     PRIMARY KEY (patient_id),
    CONSTRAINT uq_patient_no  UNIQUE (patient_no),
    CONSTRAINT ck_patient_dob CHECK (date_of_birth < '2026-01-01')
) ENGINE=InnoDB;

CREATE INDEX ix_patient_name    ON patient (full_name);
CREATE INDEX ix_patient_contact ON patient (contact_number);

-- ---------------------------------------------------------------------
--  treatment_type  -  prices live in data (ASM-11), duration in data
--  (ASM-07). Changing a price is an UPDATE, not a recompilation.
-- ---------------------------------------------------------------------
CREATE TABLE treatment_type (
    treatment_id     BIGINT        NOT NULL AUTO_INCREMENT,
    code             VARCHAR(6)    NOT NULL,
    name             VARCHAR(80)   NOT NULL,
    price            DECIMAL(10,2) NOT NULL,
    duration_minutes SMALLINT      NOT NULL,
    active           BOOLEAN       NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_treatment      PRIMARY KEY (treatment_id),
    CONSTRAINT uq_treatment_code UNIQUE (code),
    CONSTRAINT ck_treatment_price CHECK (price >= 0),
    CONSTRAINT ck_treatment_dur   CHECK (duration_minutes BETWEEN 5 AND 480)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
--  appointment
--  end_time is stored, not derived at query time, because the overlap
--  trigger and the clash query both need to compare ranges cheaply.
-- ---------------------------------------------------------------------
CREATE TABLE appointment (
    appointment_id   BIGINT      NOT NULL AUTO_INCREMENT,
    appointment_no   VARCHAR(20) NULL,          -- set by trg_appointment_number
    patient_id       BIGINT      NOT NULL,
    dentist_id       BIGINT      NOT NULL,
    appointment_date DATE        NOT NULL,
    start_time       TIME        NOT NULL,
    end_time         TIME        NOT NULL,
    status           ENUM('BOOKED','COMPLETED','CANCELLED','NO_SHOW')
                     NOT NULL DEFAULT 'BOOKED',   -- ASM-05
    notes            VARCHAR(300) NULL,
    cancel_reason    VARCHAR(200) NULL,
    created_by       BIGINT      NOT NULL,
    created_on       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_appointment      PRIMARY KEY (appointment_id),
    CONSTRAINT uq_appointment_no   UNIQUE (appointment_no),
    CONSTRAINT fk_appt_patient FOREIGN KEY (patient_id) REFERENCES patient (patient_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_appt_dentist FOREIGN KEY (dentist_id) REFERENCES dentist (dentist_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_appt_creator FOREIGN KEY (created_by) REFERENCES staff (staff_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_appt_times   CHECK (end_time > start_time)
) ENGINE=InnoDB;

-- The index the clash query in AppointmentRepository.findClashes() uses.
CREATE INDEX ix_appt_dentist_slot ON appointment (dentist_id, appointment_date, status, start_time);
CREATE INDEX ix_appt_patient      ON appointment (patient_id);
CREATE INDEX ix_appt_date         ON appointment (appointment_date, status);

-- ---------------------------------------------------------------------
--  appointment_treatment  -  resolves the many-to-many between
--  appointment and treatment_type (a visit may involve several
--  treatments).
--
--  unit_price is a SNAPSHOT of the price at the time of booking. Without
--  it, an administrator raising a price would silently change the value
--  of every historical bill. This is the classic temporal-data argument
--  and is worth a paragraph in the report.
-- ---------------------------------------------------------------------
CREATE TABLE appointment_treatment (
    appointment_id BIGINT        NOT NULL,
    treatment_id   BIGINT        NOT NULL,
    quantity       SMALLINT      NOT NULL DEFAULT 1,
    unit_price     DECIMAL(10,2) NOT NULL,
    CONSTRAINT pk_appt_treat PRIMARY KEY (appointment_id, treatment_id),
    CONSTRAINT fk_at_appt  FOREIGN KEY (appointment_id) REFERENCES appointment (appointment_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_at_treat FOREIGN KEY (treatment_id) REFERENCES treatment_type (treatment_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_at_qty   CHECK (quantity > 0)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
--  bill  /  bill_line
--
--  ON DELETE CASCADE on bill_line is the COMPOSITION from the class
--  diagram expressed in DDL: a bill line cannot outlive its bill.
--  Contrast fk_appt_patient above, which is RESTRICT because Patient
--  and Appointment are a plain association, not ownership.
-- ---------------------------------------------------------------------
CREATE TABLE bill (
    bill_id           BIGINT        NOT NULL AUTO_INCREMENT,
    bill_no           VARCHAR(20)   NULL,
    appointment_id    BIGINT        NOT NULL,
    issued_on         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    consultation_fee  DECIMAL(10,2) NOT NULL,
    treatment_subtotal DECIMAL(12,2) NOT NULL,
    discount_amount   DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    discount_label    VARCHAR(40)   NOT NULL DEFAULT 'No discount',
    total_payable     DECIMAL(12,2) NOT NULL,
    payment_status    ENUM('UNPAID','PAID') NOT NULL DEFAULT 'UNPAID',
    CONSTRAINT pk_bill        PRIMARY KEY (bill_id),
    CONSTRAINT uq_bill_no     UNIQUE (bill_no),
    CONSTRAINT uq_bill_appt   UNIQUE (appointment_id),   -- 1 : 0..1 from the class diagram
    CONSTRAINT fk_bill_appt   FOREIGN KEY (appointment_id) REFERENCES appointment (appointment_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_bill_amounts CHECK (discount_amount >= 0 AND total_payable >= 0)
) ENGINE=InnoDB;

CREATE TABLE bill_line (
    bill_id     BIGINT        NOT NULL,
    line_no     SMALLINT      NOT NULL,
    description VARCHAR(120)  NOT NULL,
    unit_price  DECIMAL(10,2) NOT NULL,
    quantity    SMALLINT      NOT NULL DEFAULT 1,
    line_total  DECIMAL(12,2) AS (unit_price * quantity) STORED,
    CONSTRAINT pk_bill_line PRIMARY KEY (bill_id, line_no),
    CONSTRAINT fk_line_bill FOREIGN KEY (bill_id) REFERENCES bill (bill_id)
        ON DELETE CASCADE                      -- <<< composition
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
--  audit_entry  -  FR-24. Who changed what, and when.
--  No patient names or contact details are written here (NFR-06); only
--  the reference number of the affected record.
-- ---------------------------------------------------------------------
CREATE TABLE audit_entry (
    entry_id     BIGINT       NOT NULL AUTO_INCREMENT,
    performed_by VARCHAR(40)  NOT NULL,
    action       VARCHAR(40)  NOT NULL,
    entity_ref   VARCHAR(40)  NOT NULL,
    detail       VARCHAR(200) NULL,
    performed_on DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_audit PRIMARY KEY (entry_id)
) ENGINE=InnoDB;

CREATE INDEX ix_audit_when ON audit_entry (performed_on);
