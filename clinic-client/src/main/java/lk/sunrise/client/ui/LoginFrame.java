package lk.sunrise.client.ui;

import lk.sunrise.client.net.ApiException;
import lk.sunrise.client.net.AuthServiceProxy;
import lk.sunrise.client.net.SessionHolder;

import javax.swing.*;
import java.awt.*;

/**
 * UC-01 Log In. FR-01, FR-05.
 *
 * Client-side validation only stops obviously empty submissions. The server
 * re-validates and is the authority - this window never decides whether a
 * password is correct.
 */
public class LoginFrame extends JFrame {

    private final AuthServiceProxy auth = new AuthServiceProxy();

    private final JTextField username = new JTextField(18);
    private final JPasswordField password = new JPasswordField(18);
    private final JLabel status = new JLabel(" ");
    private final JButton signIn = Ui.primary("Sign in");

    public LoginFrame() {
        super("Sunrise Dental Clinic - Sign in");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        add(Ui.header("Sunrise Dental Clinic",
                "Appointment and patient management"), BorderLayout.NORTH);
        add(buildForm(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        getRootPane().setDefaultButton(signIn);
        signIn.addActionListener(e -> attemptLogin());

        pack();
        setMinimumSize(new Dimension(460, getHeight()));
        setLocationRelativeTo(null);
        SwingUtilities.invokeLater(username::requestFocusInWindow);
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Color.WHITE);
        form.setBorder(BorderFactory.createEmptyBorder(24, 28, 8, 28));

        GridBagConstraints c = new GridBagConstraints();
        Ui.addRow(form, c, 0, "Username", username);
        Ui.addRow(form, c, 1, "Password", password);

        status.setForeground(Ui.DANGER);
        status.setFont(Ui.BODY);
        c.gridx = 1;
        c.gridy = 2;
        c.insets = new Insets(2, 8, 6, 10);
        form.add(status, c);

        return form;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        footer.setBackground(Ui.SURFACE);
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Ui.LINE));

        JButton quit = Ui.secondary("Exit");
        quit.addActionListener(e -> System.exit(0));

        footer.add(quit);
        footer.add(signIn);
        return footer;
    }

    private void attemptLogin() {
        status.setText(" ");

        String user = username.getText().trim();
        String pass = new String(password.getPassword());

        if (user.isEmpty() || pass.isEmpty()) {
            status.setText("Enter both a username and a password.");
            return;
        }

        // The wait cursor signals progress. The button is deliberately NOT
        // disabled: a failed attempt must leave it usable without needing a
        // re-enable path on every error branch.
        Ui.background(getRootPane(),
                () -> auth.login(user, pass),
                () -> {
                    if (SessionHolder.getInstance().isSignedIn()) {
                        dispose();
                        new MainMenuFrame().setVisible(true);
                    }
                },
                this);
    }
}
