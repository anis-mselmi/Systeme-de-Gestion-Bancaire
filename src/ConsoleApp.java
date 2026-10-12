import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.NoSuchElementException;
import java.util.Scanner;

public final class ConsoleApp {
    private static final Scanner INPUT = new Scanner(System.in);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ConsoleApp() {}

    public static void run(BankService bank) {
        System.out.println("=== Console Banking Management System ===");
        if (bank.startupWarning() != null) System.out.println(bank.startupWarning());
        boolean running = true;
        while (running) {
            printMenu();
            try {
                switch (readLine("Choose an option: ")) {
                    case "1" -> createAccount(bank);
                    case "2" -> deposit(bank);
                    case "3" -> withdraw(bank);
                    case "4" -> transfer(bank);
                    case "5" -> showBalance(bank);
                    case "6" -> showTransactions(bank);
                    case "7" -> listAccounts(bank);
                    case "8" -> exportTransactions(bank);
                    case "0" -> {
                        running = false;
                        System.out.println("Thank you for using the banking system.");
                    }
                    default -> System.out.println("Invalid option. Please choose from the menu.");
                }
                if (bank.saveWarning() != null) System.out.println(bank.saveWarning());
            } catch (NoSuchElementException exception) {
                running = false;
                System.out.println("\nInput closed. Exiting the banking system.");
            } catch (IllegalArgumentException exception) {
                System.out.println(exception.getMessage());
            }
            if (running) System.out.println();
        }
    }

    private static void printMenu() {
        System.out.println("1. Create account");
        System.out.println("2. Deposit money");
        System.out.println("3. Withdraw money");
        System.out.println("4. Transfer money");
        System.out.println("5. View account balance");
        System.out.println("6. View transaction history");
        System.out.println("7. List all accounts");
        System.out.println("8. Export account transactions to CSV");
        System.out.println("0. Exit");
    }

    private static void createAccount(BankService bank) {
        Account account = bank.createAccount(readLine("Account holder name: "));
        System.out.println("Account created. Number: " + account.number());
    }

    private static void deposit(BankService bank) {
        String number = readLine("Account number: ");
        bank.deposit(number, readAmount("Amount to deposit: "));
        System.out.println("Deposit successful. New balance: " + money(bank.account(number).balance()));
    }

    private static void withdraw(BankService bank) {
        String number = readLine("Account number: ");
        bank.withdraw(number, readAmount("Amount to withdraw: "));
        System.out.println("Withdrawal successful. New balance: " + money(bank.account(number).balance()));
    }

    private static void transfer(BankService bank) {
        String source = readLine("From account number: ");
        String destination = readLine("To account number: ");
        bank.transfer(source, destination, readAmount("Amount to transfer: "));
        System.out.println("Transfer successful.");
    }

    private static void showBalance(BankService bank) {
        Account account = bank.account(readLine("Account number: "));
        System.out.println("Account holder: " + account.holder());
        System.out.println("Balance: " + money(account.balance()));
    }

    private static void showTransactions(BankService bank) {
        Account account = bank.account(readLine("Account number: "));
        System.out.println("Transaction history for " + account.holder() + " (" + account.number() + "): ");
        if (account.transactions().isEmpty()) {
            System.out.println("No transactions yet.");
            return;
        }
        for (Transaction transaction : account.transactions()) {
            System.out.printf("%s | %-30s | %10s | Balance: %s%n",
                    transaction.time().format(TIME_FORMAT), transaction.description(),
                    money(transaction.amount()), money(transaction.balanceAfter()));
        }
    }

    private static void listAccounts(BankService bank) {
        if (bank.accounts().isEmpty()) {
            System.out.println("No accounts have been created yet.");
            return;
        }
        System.out.printf("%-12s %-25s %12s%n", "Number", "Holder", "Balance");
        for (Account account : bank.accounts()) {
            System.out.printf("%-12s %-25s %12s%n",
                    account.number(), account.holder(), money(account.balance()));
        }
    }

    private static void exportTransactions(BankService bank) {
        Account account = bank.account(readLine("Account number: "));
        Path destination = Path.of("statement-" + account.number() + ".csv");
        try {
            bank.exportStatement(account, destination);
            System.out.println("Statement exported to " + destination.toAbsolutePath());
        } catch (java.io.IOException exception) {
            System.out.println("Could not export statement: " + exception.getMessage());
        }
    }

    private static BigDecimal readAmount(String prompt) {
        try {
            BigDecimal amount = new BigDecimal(readLine(prompt).trim()).setScale(2, RoundingMode.UNNECESSARY);
            if (amount.signum() <= 0) throw new NumberFormatException();
            return amount;
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("Enter a positive amount with no more than two decimal places.");
        }
    }

    private static String readLine(String prompt) {
        System.out.print(prompt);
        return INPUT.nextLine();
    }

    private static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }
}
