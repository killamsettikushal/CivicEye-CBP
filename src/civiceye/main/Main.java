
package civiceye.main;

import javax.swing.SwingUtilities;
import civiceye.ui.LoginFrame;

public class Main {

    public static void main(String[] args) {

        // Start the GUI on Swing's event-dispatch thread
        SwingUtilities.invokeLater(() -> {

            LoginFrame login = new LoginFrame();
            login.setVisible(true);

        });
    }
}
