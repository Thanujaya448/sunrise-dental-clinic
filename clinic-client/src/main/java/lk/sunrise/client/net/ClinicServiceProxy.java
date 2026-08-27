package lk.sunrise.client.net;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PROXY for the patient, appointment, billing and report operations.
 *
 * Returns Jackson nodes rather than mirrored DTO classes. That is a
 * deliberate trade-off for a client of this size: it avoids maintaining a
 * second copy of every DTO in tier 1, at the cost of losing compile-time
 * field checking. The report states both sides.
 */
public class ClinicServiceProxy extends RestClient {

    // ---- reference data ---------------------------------------------
    public JsonNode dentists() {
        return get("/dentists");
    }

    public JsonNode treatments() {
        return get("/treatments");
    }

    // ---- patients -----------------------------------------------------
    public JsonNode searchPatients(String term) {
        return get("/patients?q=" + java.net.URLEncoder.encode(
                term == null ? "" : term, java.nio.charset.StandardCharsets.UTF_8));
    }

    public JsonNode registerPatient(String fullName, String address, String contactNumber,
                                    String email, LocalDate dateOfBirth, boolean staffFamily) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fullName", fullName);
        body.put("address", address);
        body.put("contactNumber", contactNumber);
        body.put("email", email);
        body.put("dateOfBirth", dateOfBirth.toString());
        body.put("staffFamily", staffFamily);
        return post("/patients", body);
    }

    // ---- appointments ---------------------------------------------------
    public JsonNode book(String patientNo, long dentistId, String treatmentCode,
                         LocalDate date, LocalTime startTime, String notes) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("patientNo", patientNo);
        body.put("dentistId", dentistId);
        body.put("treatmentCode", treatmentCode);
        body.put("appointmentDate", date.toString());
        body.put("startTime", startTime.toString());
        body.put("notes", notes);
        return post("/appointments", body);
    }

    public JsonNode findAppointment(String appointmentNo) {
        return get("/appointments/" + appointmentNo);
    }

    public JsonNode appointmentsOn(LocalDate date) {
        return get("/appointments?date=" + date);
    }

    public void cancel(String appointmentNo, String reason) {
        post("/appointments/" + appointmentNo + "/cancel", Map.of("reason", reason));
    }

    public void complete(String appointmentNo) {
        post("/appointments/" + appointmentNo + "/complete", Map.of());
    }

    public void noShow(String appointmentNo) {
        post("/appointments/" + appointmentNo + "/no-show", Map.of());
    }

    // ---- billing -----------------------------------------------------------
    public JsonNode generateBill(String appointmentNo) {
        return post("/bills", Map.of("appointmentNo", appointmentNo));
    }

    public JsonNode findBill(String billNo) {
        return get("/bills/" + billNo);
    }

    // ---- reports ------------------------------------------------------------
    public JsonNode reportCatalogue() {
        return get("/reports");
    }

    public JsonNode runReport(String type) {
        return get("/reports/" + type);
    }
}
