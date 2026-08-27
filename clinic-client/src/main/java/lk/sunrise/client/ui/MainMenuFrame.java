package lk.sunrise.client.ui;

import lk.sunrise.client.net.AuthServiceProxy;
import lk.sunrise.client.net.SessionHolder;

import javax.swing.*;
import java.awt.*;

/**
 * UC-02 / UC-03. The menu-driven entry point the brief asks for.
 *
 * FR-05: the options shown depend on the signed-in role. Hiding a button is
 * convenience, not security - every endpoint behind these buttons authorises
 * again on the server.
 */
public class MainMenuFrame extends JFrame {

    private static final String RECEPTION = "RECEPTIONIST";
    private static final String DENTIST   = "DENTIST";
    private static final String ADMIN     = "ADMINISTRATOR";

    private final SessionHolder session = SessionHolder.getInstance();

    public MainMenuFrame() {
        super("Sunrise Dental Clinic - Main menu");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout());

        add(Ui.header("Welcome, " + session.getFullName(),
                roleLabel() + "  -  session expires at " + session.getExpiresAt().toLocalTime()
                        .withSecond(0).withNano(0)), BorderLayout.NORTH);
        add(buildMenu(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        // UC-03: closing the window signs out properly rather than abandoning
        // the session on the server.
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                exitSafely();
            }
        });

        pack();
        setMinimumSize(new Dimension(620, getHeight()));
        setLocationRelativeTo(null);
    }

    private String roleLabel() {
        return switch (session.getRole()) {
            case RECEPTION -> "Receptionist";
            case DENTIST   -> "Dentist";
            case ADMIN     -> "Administrator";
            default        -> session.getRole();
        };
    }

    private JPanel buildMenu() {
        JPanel grid = new JPanel(new GridLayout(0, 2, 12, 12));
        grid.setBackground(Color.WHITE);
        grid.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        if (session.hasRole(RECEPTION, ADMIN)) {
            grid.add(tile("Book appointment",
                    "Register or find a patient and reserve a slot",
                    () -> new BookAppointmentFrame().setVisible(true)));
        }

        grid.add(tile("Search appointment",
                "Look up by appointment number or browse a day",
                () -> new SearchAppointmentFrame().setVisible(true)));

        if (session.hasRole(RECEPTION, ADMIN)) {
            grid.add(tile("Billing and receipts",
                    "Generate a bill for a completed visit and print it",
                    () -> new BillingFrame().setVisible(true)));
        }

        if (session.hasRole(ADMIN)) {
            grid.add(tile("Management reports",
                    "Revenue, utilisation, treatment mix and attendance",
                    () -> new ReportFrame().setVisible(true)));
        }

        grid.add(tile("Help",
                "Step-by-step instructions for clinic staff",
                () -> new HelpFrame().setVisible(true)));

        return grid;
    }

    private JPanel tile(String title, String description, Runnable action) {
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Ui.LINE),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));

        JLabel t = new JLabel(title);
        t.setFont(Ui.H2);
        t.setForeground(Ui.INK);

        JLabel d = new JLabel("<html><body style='width:200px'>" + description + "</body></html>");
        d.setFont(Ui.BODY);
        d.setForeground(new Color(0x63, 0x80, 0x7C));

        JButton open = Ui.primary("Open");
        open.addActionListener(e -> action.run());

        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
        south.setOpaque(false);
        south.add(open);

        card.add(t, BorderLayout.NORTH);
        card.add(d, BorderLayout.CENTER);
        card.add(south, BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        footer.setBackground(Ui.SURFACE);
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Ui.LINE));

        JButton signOut = Ui.secondary("Sign out");
        signOut.addActionListener(e -> exitSafely());
        footer.add(signOut);
        return footer;
    }

    /** UC-03 Exit System - clears the server session, then closes cleanly. */
    private void exitSafely() {
        int choice = JOptionPane.showConfirmDialog(this,
                "Sign out and close the application?",
                "Confirm", JOptionPane.YES_NO_OPTION);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        new AuthServiceProxy().logout();
        dispose();
        System.exit(0);
    }
}
