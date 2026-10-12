import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

public final class BankFrame extends JFrame {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final BankService bank;
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[]{"Account number", "Account holder", "Balance"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable accountTable = new JTable(tableModel);
    private final JTextField searchField = new JTextField(24);
    private final JLabel summary = new JLabel();
    private final JLabel status = new JLabel("Ready");
    private String filter = "";

    public BankFrame(BankService bank) {
        super("Northstar Banking System");
        this.bank = bank;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(780, 540));
        setSize(980, 650);
        setLocationRelativeTo(null);
        setJMenuBar(createMenuBar());

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 6, 8));
        root.add(createToolBar(), BorderLayout.NORTH);
        root.add(createMainPanel(), BorderLayout.CENTER);
        root.add(createStatusBar(), BorderLayout.SOUTH);
        setContentPane(root);

        configureTable();
        refreshAccounts(null);
        if (bank.startupWarning() != null) {
            status.setText("Warning: saved data could not be loaded. Existing file was preserved.");
        }
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        fileMenu.add(menuItem("Export selected account...", this::exportStatement));
        fileMenu.addSeparator();
        fileMenu.add(menuItem("Exit", this::dispose));

        JMenu accountMenu = new JMenu("Accounts");
        accountMenu.add(menuItem("Create account...", this::createAccount));
        accountMenu.add(menuItem("View transactions...", this::showHistory));

        JMenu transactionMenu = new JMenu("Transactions");
        transactionMenu.add(menuItem("Deposit...", this::deposit));
        transactionMenu.add(menuItem("Withdraw...", this::withdraw));
        transactionMenu.add(menuItem("Transfer...", this::transfer));

        JMenu helpMenu = new JMenu("Help");
        helpMenu.add(menuItem("About", () -> JOptionPane.showMessageDialog(this,
                "Northstar Banking System\nA local console and desktop banking application.",
                "About", JOptionPane.INFORMATION_MESSAGE)));

        menuBar.add(fileMenu);
        menuBar.add(accountMenu);
        menuBar.add(transactionMenu);
        menuBar.add(helpMenu);
        return menuBar;
    }

    private JMenuItem menuItem(String text, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(event -> action.run());
        return item;
    }

    private JToolBar createToolBar() {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setRollover(true);
        toolBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEtchedBorder(), BorderFactory.createEmptyBorder(3, 4, 3, 4)));
        toolBar.add(toolButton("New Account", this::createAccount));
        toolBar.addSeparator();
        toolBar.add(toolButton("Deposit", this::deposit));
        toolBar.add(toolButton("Withdraw", this::withdraw));
        toolBar.add(toolButton("Transfer", this::transfer));
        toolBar.addSeparator();
        toolBar.add(toolButton("Transactions", this::showHistory));
        toolBar.add(toolButton("Export CSV", this::exportStatement));
        return toolBar;
    }

    private JButton toolButton(String text, Runnable action) {
        JButton button = new JButton(text);
        button.setFocusable(false);
        button.addActionListener(event -> action.run());
        return button;
    }

    private JPanel createMainPanel() {
        JPanel main = new JPanel(new BorderLayout(8, 10));

        JPanel heading = new JPanel(new BorderLayout(0, 7));
        heading.setBorder(BorderFactory.createEmptyBorder(8, 5, 2, 5));
        JLabel title = new JLabel("Accounts");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        heading.add(title, BorderLayout.NORTH);
        summary.setBorder(BorderFactory.createTitledBorder("Summary"));
        heading.add(summary, BorderLayout.CENTER);
        main.add(heading, BorderLayout.NORTH);

        JPanel tablePanel = new JPanel(new BorderLayout(0, 7));
        tablePanel.setBorder(BorderFactory.createTitledBorder("Account list"));
        tablePanel.add(createSearchPanel(), BorderLayout.NORTH);
        tablePanel.add(new JScrollPane(accountTable), BorderLayout.CENTER);
        main.add(tablePanel, BorderLayout.CENTER);
        return main;
    }

    private JPanel createSearchPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 5));
        panel.add(new JLabel("Find account:"));
        panel.add(searchField);
        JButton clearButton = new JButton("Clear");
        clearButton.addActionListener(event -> searchField.setText(""));
        panel.add(clearButton);
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            private void updateFilter() {
                filter = searchField.getText().trim().toLowerCase();
                refreshAccounts(null);
            }

            @Override public void insertUpdate(DocumentEvent event) { updateFilter(); }
            @Override public void removeUpdate(DocumentEvent event) { updateFilter(); }
            @Override public void changedUpdate(DocumentEvent event) { updateFilter(); }
        });
        return panel;
    }

    private JPanel createStatusBar() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEtchedBorder(), BorderFactory.createEmptyBorder(4, 7, 4, 7)));
        panel.add(status, BorderLayout.WEST);
        panel.add(new JLabel("Data file: " + bank.dataFile()), BorderLayout.EAST);
        return panel;
    }

    private void configureTable() {
        accountTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        accountTable.setRowHeight(26);
        accountTable.setFillsViewportHeight(true);
        accountTable.setAutoCreateRowSorter(true);
        accountTable.setShowGrid(true);
        accountTable.getTableHeader().setReorderingAllowed(false);
        DefaultTableCellRenderer balanceRenderer = new DefaultTableCellRenderer();
        balanceRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
        accountTable.getColumnModel().getColumn(2).setCellRenderer(balanceRenderer);
        accountTable.getColumnModel().getColumn(0).setPreferredWidth(130);
        accountTable.getColumnModel().getColumn(1).setPreferredWidth(330);
        accountTable.getColumnModel().getColumn(2).setPreferredWidth(150);
    }

    private void createAccount() {
        String holder = JOptionPane.showInputDialog(this, "Enter the account holder's name:", "Create account",
                JOptionPane.PLAIN_MESSAGE);
        if (holder == null) return;
        try {
            Account account = bank.createAccount(holder);
            refreshAccounts(account.number());
            updateStatus("Account " + account.number() + " created.");
        } catch (IllegalArgumentException exception) {
            showError(exception.getMessage());
        }
    }

    private void deposit() {
        Account account = selectedAccount();
        if (account == null) return;
        BigDecimal amount = promptAmount("Enter the deposit amount:", "Deposit");
        if (amount == null) return;
        try {
            bank.deposit(account.number(), amount);
            refreshAccounts(account.number());
            updateStatus("Deposit completed for account " + account.number() + ".");
        } catch (IllegalArgumentException exception) {
            showError(exception.getMessage());
        }
    }

    private void withdraw() {
        Account account = selectedAccount();
        if (account == null) return;
        BigDecimal amount = promptAmount("Enter the withdrawal amount:", "Withdraw");
        if (amount == null) return;
        try {
            bank.withdraw(account.number(), amount);
            refreshAccounts(account.number());
            updateStatus("Withdrawal completed for account " + account.number() + ".");
        } catch (IllegalArgumentException exception) {
            showError(exception.getMessage());
        }
    }

    private void transfer() {
        if (bank.accounts().size() < 2) {
            showError("Create at least two accounts before making a transfer.");
            return;
        }
        JComboBox<Account> source = new JComboBox<>(bank.accounts().toArray(new Account[0]));
        JComboBox<Account> destination = new JComboBox<>(bank.accounts().toArray(new Account[0]));
        Account selected = selectedAccountOrNull();
        if (selected != null) source.setSelectedItem(selected);

        JPanel fields = new JPanel(new GridLayout(2, 2, 6, 6));
        fields.add(new JLabel("From account:"));
        fields.add(source);
        fields.add(new JLabel("To account:"));
        fields.add(destination);
        if (JOptionPane.showConfirmDialog(this, fields, "Transfer", JOptionPane.OK_CANCEL_OPTION)
                != JOptionPane.OK_OPTION) return;

        Account from = (Account) source.getSelectedItem();
        Account to = (Account) destination.getSelectedItem();
        BigDecimal amount = promptAmount("Enter the transfer amount:", "Transfer");
        if (amount == null) return;
        try {
            bank.transfer(from.number(), to.number(), amount);
            refreshAccounts(from.number());
            updateStatus("Transfer completed from " + from.number() + " to " + to.number() + ".");
        } catch (IllegalArgumentException exception) {
            showError(exception.getMessage());
        }
    }

    private void showHistory() {
        Account account = selectedAccount();
        if (account == null) return;
        DefaultTableModel historyModel = new DefaultTableModel(
                new Object[]{"Date and time", "Description", "Amount", "Balance after"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        for (Transaction transaction : account.transactions()) {
            historyModel.addRow(new Object[]{transaction.time().format(TIME_FORMAT), transaction.description(),
                    money(transaction.amount()), money(transaction.balanceAfter())});
        }
        JTable historyTable = new JTable(historyModel);
        historyTable.setRowHeight(24);
        JScrollPane scrollPane = new JScrollPane(historyTable);
        scrollPane.setPreferredSize(new Dimension(650, 320));
        JOptionPane.showMessageDialog(this, scrollPane,
                "Transactions for " + account.holder() + " (" + account.number() + ")",
                JOptionPane.PLAIN_MESSAGE);
    }

    private void exportStatement() {
        Account account = selectedAccount();
        if (account == null) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(Path.of("statement-" + account.number() + ".csv").toFile());
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            bank.exportStatement(account, chooser.getSelectedFile().toPath());
            updateStatus("Statement exported to " + chooser.getSelectedFile().getName() + ".");
        } catch (IOException exception) {
            showError("Could not export statement: " + exception.getMessage());
        }
    }

    private BigDecimal promptAmount(String prompt, String title) {
        String input = JOptionPane.showInputDialog(this, prompt, title, JOptionPane.PLAIN_MESSAGE);
        if (input == null) return null;
        try {
            BigDecimal amount = new BigDecimal(input.trim()).setScale(2, RoundingMode.UNNECESSARY);
            if (amount.signum() <= 0) throw new NumberFormatException();
            return amount;
        } catch (NumberFormatException | ArithmeticException exception) {
            showError("Enter a positive amount with no more than two decimal places.");
            return null;
        }
    }

    private Account selectedAccount() {
        Account account = selectedAccountOrNull();
        if (account == null) showError("Select an account from the list first.");
        return account;
    }

    private Account selectedAccountOrNull() {
        int row = accountTable.getSelectedRow();
        if (row < 0) return null;
        int modelRow = accountTable.convertRowIndexToModel(row);
        String number = tableModel.getValueAt(modelRow, 0).toString();
        return bank.account(number);
    }

    private void refreshAccounts(String selectedNumber) {
        tableModel.setRowCount(0);
        int selectedModelRow = -1;
        for (Account account : bank.accounts()) {
            if (!filter.isEmpty() && !account.number().contains(filter)
                    && !account.holder().toLowerCase().contains(filter)) continue;
            tableModel.addRow(new Object[]{account.number(), account.holder(), money(account.balance())});
            if (account.number().equals(selectedNumber)) selectedModelRow = tableModel.getRowCount() - 1;
        }

        summary.setText("Accounts: " + bank.accounts().size() + "     Combined balance: "
                + money(bank.totalBalance()) + "     Transactions: " + bank.transactionCount());
        if (selectedModelRow >= 0) {
            int viewRow = accountTable.convertRowIndexToView(selectedModelRow);
            if (viewRow >= 0) accountTable.setRowSelectionInterval(viewRow, viewRow);
        } else {
            accountTable.clearSelection();
        }
    }

    private void updateStatus(String successMessage) {
        if (bank.saveWarning() == null) {
            status.setText(successMessage + " Changes saved.");
        } else {
            status.setText(bank.saveWarning());
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Northstar Banking", JOptionPane.ERROR_MESSAGE);
    }

    private static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }
}
