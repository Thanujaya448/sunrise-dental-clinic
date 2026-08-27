package lk.sunrise.client.ui;

import com.fasterxml.jackson.databind.JsonNode;
import lk.sunrise.client.net.ClinicServiceProxy;

import javax.swing.*;
import java.awt.*;
import java.awt.print.PrinterException;

/**
 * UC-15 Generate Bill and UC-18 Print Receipt. FR-17 to FR-19.
 *
 * The window performs no arithmetic. Every figure shown comes from the
 * service, which took the subtotal from fn_treatment_subtotal, the discount
 * from the Strategy, and wrote the bill through sp_generate_bill. Keeping the
 * maths out of the UI is what makes the tier separation real.
 */
public class BillingFrame extends JFrame {

    private final ClinicServiceProxy api = new ClinicServiceProxy();

    private final JTextField appointmentNo = new JTextField(18);
    private final JTextArea receipt = new JTextArea(18, 52);
    private final JButton print = Ui.secondary("Print receipt");

    public BillingFrame() {
        super("Billing and receipts");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        add(Ui.header("Billing", "Generate the bill for a completed appointment"),
                BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        add(buildActions(), BorderLayout.SOUTH);

        pack();
        setMinimumSize(new Dimension(700, 600));
        setLocationRelativeTo(null);
    }

    private JComponent buildBody() {
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 10));
        top.setBackground(Color.WHITE);

        JButton generate = Ui.primary("Generate bill");
        generate.setPreferredSize(new Dimension(150, 30));
        generate.addActionListener(e -> generateBill());

        JButton lookup = Ui.secondary("Find existing bill");
        lookup.setPreferredSize(new Dimension(150, 30));
        lookup.addActionListener(e -> findBill());

        top.add(new JLabel("Appointment number:"));
        top.add(appointmentNo);
        top.add(generate);
        top.add(lookup);

        appointmentNo.addActionListener(e -> generateBill());

        receipt.setEditable(false);
        receipt.setFont(Ui.MONO);
        receipt.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        JPanel body = new JPanel(new BorderLayout());
        body.add(top, BorderLayout.NORTH);
        body.add(new JScrollPane(receipt), BorderLayout.CENTER);
        return body;
    }

    private JPanel buildActions() {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        actions.setBackground(Ui.SURFACE);
        actions.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Ui.LINE));

        print.setEnabled(false);
        print.setPreferredSize(new Dimension(150, 34));
        print.addActionListener(e -> printReceipt());

        JButton close = Ui.secondary("Close");
        close.addActionListener(e -> dispose());

        actions.add(close);
        actions.add(print);
        return actions;
    }

    private void generateBill() {
        String no = appointmentNo.getText().trim();
        if (no.isEmpty()) {
            Ui.warn(this, "Enter the appointment number, for example APT-2026-000005.");
            return;
        }
        final JsonNode[] bill = new JsonNode[1];
        Ui.background(getRootPane(),
                () -> bill[0] = api.generateBill(no),
                () -> show(bill[0]),
                this);
    }

    private void findBill() {
        String no = JOptionPane.showInputDialog(this,
                "Bill number:", "Find bill", JOptionPane.QUESTION_MESSAGE);
        if (no == null || no.isBlank()) {
            return;
        }
        final JsonNode[] bill = new JsonNode[1];
        Ui.background(getRootPane(),
                () -> bill[0] = api.findBill(no.trim()),
                () -> show(bill[0]),
                this);
    }

    private void show(JsonNode b) {
        StringBuilder sb = new StringBuilder();
        sb.append("            SUNRISE DENTAL CLINIC\n");
        sb.append("        14 Temple Road, Nugegoda, Sri Lanka\n");
        sb.append("=".repeat(56)).append('\n');
        sb.append(String.format("Bill number    : %s%n", b.get("billNo").asText()));
        sb.append(String.format("Appointment    : %s%n", b.get("appointmentNo").asText()));
        sb.append(String.format("Patient        : %s%n", b.get("patientName").asText()));
        sb.append(String.format("Issued         : %s%n", b.get("issuedOn").asText().replace('T', ' ')));
        sb.append("=".repeat(56)).append('\n');
        sb.append(String.format("%-32s %6s %13s%n", "Description", "Qty", "Amount"));
        sb.append("-".repeat(56)).append('\n');

        b.get("lines").forEach(l -> sb.append(String.format("%-32s %6d %13s%n",
                truncate(l.get("description").asText(), 32),
                l.get("quantity").asInt(),
                l.get("lineTotal").asText())));

        sb.append("-".repeat(56)).append('\n');
        sb.append(String.format("%-39s %16s%n", "Consultation fee", b.get("consultationFee").asText()));
        sb.append(String.format("%-39s %16s%n", "Treatment subtotal", b.get("treatmentSubtotal").asText()));
        sb.append(String.format("%-39s %16s%n",
                b.get("discountLabel").asText(), "-" + b.get("discountAmount").asText()));
        sb.append("=".repeat(56)).append('\n');
        sb.append(String.format("%-39s %16s%n", "TOTAL PAYABLE (LKR)", b.get("totalPayable").asText()));
        sb.append(String.format("%-39s %16s%n", "Payment status", b.get("paymentStatus").asText()));
        sb.append("=".repeat(56)).append('\n');
        sb.append("\n     Thank you for visiting Sunrise Dental Clinic.\n");

        receipt.setText(sb.toString());
        receipt.setCaretPosition(0);
        print.setEnabled(true);
    }

    /** UC-18 - JTextArea.print() hands the text to the real print dialog. */
    private void printReceipt() {
        try {
            boolean printed = receipt.print();
            if (printed) {
                Ui.info(this, "Receipt sent to the printer.");
            }
        } catch (PrinterException ex) {
            Ui.error(this, "The receipt could not be printed: " + ex.getMessage());
        }
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
