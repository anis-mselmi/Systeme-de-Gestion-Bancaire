import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.metal.MetalLookAndFeel;

public class Main {
    public static void main(String[] args) {
        BankService bank = new BankService();
        if (args.length > 0 && args[0].equalsIgnoreCase("--console")) {
            ConsoleApp.run(bank);
            return;
        }

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(new MetalLookAndFeel());
            } catch (Exception ignored) {
                // Swing uses its default look and feel if Metal cannot be loaded.
            }
            new BankFrame(bank).setVisible(true);
        });
    }
}
hhu