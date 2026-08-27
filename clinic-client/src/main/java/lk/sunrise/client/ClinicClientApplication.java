package lk.sunrise.client;

import lk.sunrise.client.ui.LoginFrame;
import lk.sunrise.client.ui.Ui;

import javax.swing.*;

/**
 * TIER 1 entry point.
 *
 * A separate process from the REST service, started independently and
 * communicating only over HTTP. That separation is what makes the system
 * distributed rather than layered.
 */
public final class ClinicClientApplication {

    private ClinicClientApplication() { }

    public static void main(String[] args) {
        Ui.applyLookAndFeel();
        // Every Swing component must be created on the event dispatch thread.
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}
