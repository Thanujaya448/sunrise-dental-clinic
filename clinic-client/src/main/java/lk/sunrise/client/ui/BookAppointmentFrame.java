package lk.sunrise.client.ui;

import com.fasterxml.jackson.databind.JsonNode;
import lk.sunrise.client.net.ApiException;
import lk.sunrise.client.net.ClinicServiceProxy;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

/**
 * UC-07 Book Appointment, with UC-05 / UC-06 patient lookup and registration.
 *
 * Covers FR-06 to FR-12 and FR-20. The most important behaviour here is the
 * clash response: on HTTP 409 the window shows the alternative slots the
 * service suggested and lets the receptionist take one, rather than simply
 * refusing (ASM-09).
 */
public class BookAppointmentFrame extends JFrame {

    private final ClinicServiceProxy api = new ClinicServiceProxy();

    // patient search
    private final JTextField searchTerm = new JTextField(16);
    private final DefaultTableModel patientModel =
            Ui.model("Patient no", "Name", "Contact", "Visits");
    private final JTable patientTable = Ui.readOnlyTable(patientModel);

    // booking form
    private final JComboBox<Item> dentist = new JComboBox<>();
    private final JComboBox<Item> treatment = new JComboBox<>();
    private final JTextField date = new JTextField(LocalDate.now().plusDays(1).toString(), 12);
    private final JTextField time = new JTextField("09:00", 12);
    private final JTextField notes = new JTextField(20);
    private final JLabel selectedPatient = new JLabel("No patient selected");

    /** Simple value holder so a combo can show a label but carry an id/code. */
    private record Item(String id, String label) {
        @Override public String toString() { return label; }
    }

    public BookAppointmentFrame() {
        super("Book appointment");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        add(Ui.header("Book an appointment",
                "Find the patient, then choose a dentist, treatment and time"), BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildPatientPanel(), buildBookingPanel());
        split.setResizeWeight(0.45);
        add(split, BorderLayout.CENTER);

        pack();
        setMinimumSize(new Dimension(980, 560));
        setLocationRelativeTo(null);

        loadReferenceData();
    }

    // =================================================================
    //  left - patient search and registration
    // =================================================================
    private JPanel buildPatientPanel() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 8));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        top.setOpaque(false);
        JButton find = Ui.secondary("Search");
        find.setPreferredSize(new Dimension(100, 30));
        JButton register = Ui.secondary("New patient");
        register.setPreferredSize(new Dimension(120, 30));

        top.add(new JLabel("Name, phone or number:"));
        top.add(searchTerm);
        top.add(find);
        top.add(register);

        find.addActionListener(e -> searchPatients());
        register.addActionListener(e -> registerPatient());
        searchTerm.addActionListener(e -> searchPatients());

        patientTable.getSelectionModel().addListSelectionListener(e -> {
            int row = patientTable.getSelectedRow();
            if (row >= 0) {
                selectedPatient.setText(patientModel.getValueAt(row, 1)
                        + "  (" + patientModel.getValueAt(row, 0) + ")");
            }
        });

        p.add(top, BorderLayout.NORTH);
        p.add(new JScrollPane(patientTable), BorderLayout.CENTER);
        return p;
    }

    private void searchPatients() {
        String term = searchTerm.getText().trim();
        final JsonNode[] result = new JsonNode[1];
        Ui.background(getRootPane(),
                () -> result[0] = api.searchPatients(term),
                () -> {
                    patientModel.setRowCount(0);
                    if (result[0] != null) {
                        result[0].forEach(n -> patientModel.addRow(new Object[]{
                                n.get("patientNo").asText(),
                                n.get("fullName").asText(),
                                n.get("contactNumber").asText(),
                                n.get("completedVisits").asInt()}));
                    }
                    if (patientModel.getRowCount() == 0) {
                        Ui.info(this, "No patient matched \"" + term
                                + "\".\nUse New patient to register them.");
                    }
                },
                this);
    }

    private void registerPatient() {
        JTextField name = new JTextField(18);
        JTextField address = new JTextField(18);
        JTextField phone = new JTextField(18);
        JTextField email = new JTextField(18);
        JTextField dob = new JTextField("1990-01-01", 18);
        JCheckBox staffFamily = new JCheckBox("Clinic staff or immediate family");

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        Ui.addRow(form, c, 0, "Full name", name);
        Ui.addRow(form, c, 1, "Address", address);
        Ui.addRow(form, c, 2, "Contact number", phone);
        Ui.addRow(form, c, 3, "Email (optional)", email);
        Ui.addRow(form, c, 4, "Date of birth", dob);
        Ui.addRow(form, c, 5, "", staffFamily);

        int ok = JOptionPane.showConfirmDialog(this, form, "Register a new patient",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) {
            return;
        }

        // FR-06 client-side validation, before troubling the service.
        if (name.getText().isBlank() || address.getText().isBlank() || phone.getText().isBlank()) {
            Ui.warn(this, "Name, address and contact number are all required.");
            return;
        }
        if (!phone.getText().trim().matches("0\\d{9}")) {
            Ui.warn(this, "Contact number must be 10 digits starting with 0, for example 0712345678.");
            return;
        }
        LocalDate birth;
        try {
            birth = LocalDate.parse(dob.getText().trim());
        } catch (DateTimeParseException ex) {
            Ui.warn(this, "Date of birth must be in the form YYYY-MM-DD.");
            return;
        }
        if (birth.isAfter(LocalDate.now())) {
            Ui.warn(this, "Date of birth cannot be in the future.");
            return;
        }

        final JsonNode[] created = new JsonNode[1];
        Ui.background(getRootPane(),
                () -> created[0] = api.registerPatient(name.getText().trim(),
                        address.getText().trim(), phone.getText().trim(),
                        email.getText().trim().isEmpty() ? null : email.getText().trim(),
                        birth, staffFamily.isSelected()),
                () -> {
                    String no = created[0].get("patientNo").asText();
                    Ui.info(this, "Patient registered as " + no + ".");
                    searchTerm.setText(no);
                    searchPatients();
                },
                this);
    }

    // =================================================================
    //  right - the booking form
    // =================================================================
    private JPanel buildBookingPanel() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createEmptyBorder(14, 8, 14, 14));

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();

        selectedPatient.setFont(Ui.H2);
        selectedPatient.setForeground(Ui.ACCENT);

        Ui.addRow(form, c, 0, "Patient", selectedPatient);
        Ui.addRow(form, c, 1, "Dentist", dentist);
        Ui.addRow(form, c, 2, "Treatment", treatment);
        Ui.addRow(form, c, 3, "Date (YYYY-MM-DD)", date);
        Ui.addRow(form, c, 4, "Start time (HH:MM)", time);
        Ui.addRow(form, c, 5, "Notes (optional)", notes);

        JButton book = Ui.primary("Book appointment");
        book.addActionListener(e -> book());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        actions.setOpaque(false);
        JButton close = Ui.secondary("Close");
        close.addActionListener(e -> dispose());
        actions.add(close);
        actions.add(book);

        p.add(form, BorderLayout.NORTH);
        p.add(actions, BorderLayout.SOUTH);
        return p;
    }

    private void loadReferenceData() {
        final JsonNode[] d = new JsonNode[1];
        final JsonNode[] t = new JsonNode[1];
        Ui.background(getRootPane(),
                () -> {
                    d[0] = api.dentists();
                    t[0] = api.treatments();
                },
                () -> {
                    d[0].forEach(n -> dentist.addItem(new Item(
                            n.get("dentistId").asText(),
                            n.get("fullName").asText() + "  -  " + n.get("specialisation").asText())));
                    t[0].forEach(n -> treatment.addItem(new Item(
                            n.get("code").asText(),
                            n.get("name").asText() + "  (" + n.get("durationMinutes").asInt()
                                    + " min, LKR " + n.get("price").asText() + ")")));
                },
                this);
    }

    private void book() {
        int row = patientTable.getSelectedRow();
        if (row < 0) {
            Ui.warn(this, "Select a patient from the list on the left first.");
            return;
        }
        if (dentist.getSelectedItem() == null || treatment.getSelectedItem() == null) {
            Ui.warn(this, "Choose a dentist and a treatment.");
            return;
        }

        LocalDate when;
        LocalTime start;
        try {
            when = LocalDate.parse(date.getText().trim());
        } catch (DateTimeParseException ex) {
            Ui.warn(this, "Date must be in the form YYYY-MM-DD, for example "
                    + LocalDate.now().plusDays(1) + ".");
            return;
        }
        try {
            start = LocalTime.parse(time.getText().trim());
        } catch (DateTimeParseException ex) {
            Ui.warn(this, "Start time must be in the form HH:MM, for example 09:30.");
            return;
        }
        if (when.isBefore(LocalDate.now())) {
            Ui.warn(this, "Appointments cannot be booked in the past.");
            return;
        }

        String patientNo = String.valueOf(patientModel.getValueAt(row, 0));
        long dentistId = Long.parseLong(((Item) dentist.getSelectedItem()).id());
        String code = ((Item) treatment.getSelectedItem()).id();

        try {
            JsonNode saved = api.book(patientNo, dentistId, code, when, start,
                    notes.getText().trim());
            Ui.info(this, "Appointment booked.\n\nNumber: " + saved.get("appointmentNo").asText()
                    + "\nDentist: " + saved.get("dentistName").asText()
                    + "\nWhen: " + saved.get("appointmentDate").asText()
                    + " at " + saved.get("startTime").asText()
                    + " until " + saved.get("endTime").asText());
            notes.setText("");
        } catch (ApiException ex) {
            if (ex.getStatus() == 409 && !ex.getSuggestedSlots().isEmpty()) {
                offerAlternatives(ex);
            } else {
                Ui.showApiError(this, ex);
            }
        }
    }

    /** FR-11 / ASM-09 - a refusal that offers a way forward. */
    private void offerAlternatives(ApiException ex) {
        Object[] options = ex.getSuggestedSlots().stream().map(LocalTime::toString).toArray();
        Object choice = JOptionPane.showInputDialog(this,
                ex.getMessage() + "\n\nChoose one of the free slots, or Cancel to pick another day.",
                "That slot is taken", JOptionPane.WARNING_MESSAGE, null, options, options[0]);
        if (choice != null) {
            time.setText(String.valueOf(choice));
            book();
        }
    }
}
