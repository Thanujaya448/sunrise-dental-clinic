package lk.sunrise.clinic.api;

import lk.sunrise.clinic.dto.Dtos;
import lk.sunrise.clinic.repository.ClinicRepository;
import lk.sunrise.clinic.domain.TreatmentType;
import lk.sunrise.clinic.service.AdministrationService;
import lk.sunrise.clinic.service.AppointmentService;
import lk.sunrise.clinic.service.AuthenticationService;
import lk.sunrise.clinic.service.BillingFacade;
import lk.sunrise.clinic.service.ReportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * TIER 2 REST API.
 *
 * Every method authorises before it acts (FR-05). The Swing client hides the
 * options a role cannot use, but hiding is convenience - this is the control.
 */
@RestController
@RequestMapping("/api")
public class ClinicController {

    private static final String RECEPTION = "RECEPTIONIST";
    private static final String DENTIST   = "DENTIST";
    private static final String ADMIN     = "ADMINISTRATOR";

    private final AuthenticationService auth;
    private final AppointmentService appointments;
    private final BillingFacade billing;
    private final ReportService reports;
    private final ClinicRepository repository;
    private final AdministrationService administration;

    public ClinicController(AuthenticationService auth, AppointmentService appointments,
                            BillingFacade billing, ReportService reports,
                            ClinicRepository repository, AdministrationService administration) {
        this.auth = auth;
        this.appointments = appointments;
        this.billing = billing;
        this.reports = reports;
        this.repository = repository;
        this.administration = administration;
    }

    // =================================================================
    //  UC-01 / UC-03  session
    // =================================================================

    @PostMapping("/auth/login")
    public Dtos.SessionDTO login(@RequestBody Dtos.LoginRequest req) {
        return auth.login(req.username(), req.password());
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = "Authorization", required = false) String token) {
        auth.logout(strip(token));
        return ResponseEntity.noContent().build();
    }

    // =================================================================
    //  reference data
    // =================================================================

    @GetMapping("/dentists")
    public List<Dtos.DentistDTO> dentists(@RequestHeader(value = "Authorization", required = false) String token) {
        auth.requireSession(strip(token));
        return repository.findActiveDentists();
    }

    // =================================================================
    //  UC-05 / UC-06  patients
    // =================================================================

    @GetMapping("/patients")
    public List<Dtos.PatientDTO> searchPatients(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam(defaultValue = "") String q) {
        auth.requireRole(strip(token), RECEPTION, ADMIN, DENTIST);
        return repository.searchPatients(q);
    }

    @PostMapping("/patients")
    public ResponseEntity<Dtos.PatientDTO> registerPatient(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody Dtos.RegisterPatientRequest req) {
        auth.requireRole(strip(token), RECEPTION, ADMIN);
        String patientNo = repository.insertPatient(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(repository.findPatientByNo(patientNo).orElseThrow());
    }

    // =================================================================
    //  UC-07 to UC-13  appointments
    // =================================================================

    @PostMapping("/appointments")
    public ResponseEntity<Dtos.AppointmentDTO> book(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody Dtos.BookingRequest req) {
        var session = auth.requireRole(strip(token), RECEPTION, ADMIN);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(appointments.book(req, session.staffId()));
    }

    @GetMapping("/appointments/{appointmentNo}")
    public Dtos.AppointmentDTO findAppointment(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String appointmentNo) {
        auth.requireSession(strip(token));
        return appointments.findByNumber(appointmentNo);
    }

    @GetMapping("/appointments")
    public List<Dtos.AppointmentDTO> appointmentsOn(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam(required = false) String date) {
        auth.requireSession(strip(token));
        return appointments.findOn(date == null ? LocalDate.now() : LocalDate.parse(date));
    }

    @PostMapping("/appointments/{appointmentNo}/cancel")
    public ResponseEntity<Void> cancel(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String appointmentNo,
            @RequestBody Map<String, String> body) {
        auth.requireRole(strip(token), RECEPTION, ADMIN);
        appointments.cancel(appointmentNo, body.get("reason"));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/appointments/{appointmentNo}/complete")
    public ResponseEntity<Void> complete(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String appointmentNo) {
        auth.requireRole(strip(token), DENTIST, ADMIN);
        appointments.markCompleted(appointmentNo);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/appointments/{appointmentNo}/no-show")
    public ResponseEntity<Void> noShow(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String appointmentNo) {
        auth.requireRole(strip(token), DENTIST, ADMIN);
        appointments.markNoShow(appointmentNo);
        return ResponseEntity.noContent().build();
    }

    // =================================================================
    //  UC-15 to UC-18  billing
    // =================================================================

    @PostMapping("/bills")
    public ResponseEntity<Dtos.BillDTO> generateBill(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody Map<String, String> body) {
        auth.requireRole(strip(token), RECEPTION, ADMIN);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(billing.generateBill(body.get("appointmentNo")));
    }

    @GetMapping("/bills/{billNo}")
    public Dtos.BillDTO findBill(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String billNo) {
        auth.requireSession(strip(token));
        return billing.findByNumber(billNo);
    }

    // =================================================================
    //  UC-21  reports  (Administrator only)
    // =================================================================

    @GetMapping("/reports")
    public List<Map<String, String>> reportCatalogue(
            @RequestHeader(value = "Authorization", required = false) String token) {
        auth.requireRole(strip(token), ADMIN);
        return reports.catalogue();
    }

    @GetMapping("/reports/{type}")
    public List<Map<String, Object>> runReport(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String type) {
        auth.requireRole(strip(token), ADMIN);
        return reports.run(type);
    }


    // =================================================================
    //  UC-16 / UC-17  administration  (Administrator only)
    //
    //  Every method opens with requireRole(ADMIN). A receptionist who
    //  discovers these URLs still receives 403 - the tab is hidden for
    //  convenience, the check is the control (FR-05).
    // =================================================================

    /** FR-21. Includes inactive treatments so one can be restored. */
    @GetMapping("/admin/treatments")
    public List<TreatmentType> allTreatments(
            @RequestHeader(value = "Authorization", required = false) String token) {
        auth.requireRole(strip(token), ADMIN);
        return administration.allTreatments();
    }

    @PostMapping("/admin/treatments")
    @ResponseStatus(HttpStatus.CREATED)
    public TreatmentType createTreatment(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody Dtos.TreatmentRequest req) {
        auth.requireRole(strip(token), ADMIN);
        return administration.createTreatment(req.code(), req.name(),
                req.price(), req.durationMinutes());
    }

    /** FR-21. Changes the standing price; booked appointments keep their snapshot. */
    @PutMapping("/admin/treatments/{code}")
    public TreatmentType updateTreatment(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String code,
            @RequestBody Dtos.TreatmentRequest req) {
        auth.requireRole(strip(token), ADMIN);
        return administration.updateTreatment(code, req.name(), req.price(),
                req.durationMinutes(), req.active());
    }

    /** FR-22. */
    @GetMapping("/admin/staff")
    public List<Map<String, Object>> allStaff(
            @RequestHeader(value = "Authorization", required = false) String token) {
        auth.requireRole(strip(token), ADMIN);
        return administration.allStaff();
    }

    @PostMapping("/admin/staff")
    @ResponseStatus(HttpStatus.CREATED)
    public void createStaff(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody Dtos.StaffRequest req) {
        auth.requireRole(strip(token), ADMIN);
        administration.createStaff(req.username(), req.password(), req.fullName(), req.role(),
                req.registrationNo(), req.specialisation(), req.consultationFee());
    }

    /** FR-03. The other half of the lockout rule. */
    @PostMapping("/admin/staff/{username}/unlock")
    public void unlockStaff(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String username) {
        auth.requireRole(strip(token), ADMIN);
        administration.unlockAccount(username);
    }

    @PostMapping("/admin/staff/{username}/active")
    public void setStaffActive(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String username,
            @RequestParam boolean active) {
        auth.requireRole(strip(token), ADMIN);
        administration.setActive(username, active);
    }

    /** Accepts both "Bearer xyz" and a bare token, so the client stays simple. */
    private String strip(String header) {
        if (header == null) {
            return null;
        }
        return header.startsWith("Bearer ") ? header.substring(7) : header;
    }
}
