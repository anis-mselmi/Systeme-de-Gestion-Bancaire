import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class Main {
    private static final Scanner INPUT = new Scanner(System.in);
    private static final Map<String, Account> ACCOUNTS = new LinkedHashMap<>();
    private static int nextAccountNumber = 1001;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) {
        System.out.println("=== Console Banking Management System ===");
        boolean running = true;
        while (running) {
            printMenu();
            switch (readLine("Choose an option: ")) {
                case "1" -> createAccount();
                case "2" -> deposit();
                case "3" -> withdraw();
                case "4" -> transfer();
                case "5" -> showBalance();
                case "6" -> showTransactions();
                case "0" -> {
                    running = false;
                    System.out.println("Thank you for using the banking system.");
                }
                default -> System.out.println("Invalid option. Please choose from the menu.");
            }
            System.out.println();
        }
    }

    private static void printMenu() {
        System.out.println("1. Create account");
        System.out.println("2. Deposit money");
        System.out.println("3. Withdraw money");
        System.out.println("4. Transfer money");
        System.out.println("5. View account balance");
        System.out.println("6. View transaction history");
        System.out.println("0. Exit");
    }

    private static void createAccount() {
        String name = readLine("Account holder name: ").trim();
        if (name.isEmpty()) {
            System.out.println("Name cannot be empty.");
            return;
        }

        String number = String.valueOf(nextAccountNumber++);
        Account account = new Account(number, name);
        ACCOUNTS.put(number, account);
        System.out.println("Account created successfully. Account number: " + number);
    }

    private static void deposit() {
        Account account = findAccount(readLine("Account number: "));
        if (account == null) return;
        BigDecimal amount = readAmount("Amount to deposit: ");
        if (amount == null) return;

        account.deposit(amount, "Deposit");
        System.out.println("Deposit successful. New balance: " + money(account.balance));
    }

    private static void withdraw() {
        Account account = findAccount(readLine("Account number: "));
        if (account == null) return;
        BigDecimal amount = readAmount("Amount to withdraw: ");
        if (amount == null) return;
        if (account.balance.compareTo(amount) < 0) {
            System.out.println("Insufficient funds. Current balance: " + money(account.balance));
            return;
        }

        account.withdraw(amount, "Withdrawal");
        System.out.println("Withdrawal successful. New balance: " + money(account.balance));
    }

    private static void transfer() {
        Account source = findAccount(readLine("From account number: "));
        if (source == null) return;
        Account destination = findAccount(readLine("To account number: "));
        if (destination == null) return;
        if (source == destination) {
            System.out.println("Source and destination accounts must be different.");
            return;
        }

        BigDecimal amount = readAmount("Amount to transfer: ");
        if (amount == null) return;
        if (source.balance.compareTo(amount) < 0) {
            System.out.println("Insufficient funds. Current balance: " + money(source.balance));
            return;
        }

        source.withdraw(amount, "Transfer to account " + destination.number);
        destination.deposit(amount, "Transfer from account " + source.number);
        System.out.println("Transfer successful.");
        System.out.println("Source balance: " + money(source.balance));
        System.out.println("Destination balance: " + money(destination.balance));
    }

    private static void showBalance() {
        Account account = findAccount(readLine("Account number: "));
        if (account != null) {
            System.out.println("Account holder: " + account.holder);
            System.out.println("Balance: " + money(account.balance));
        }
    }

    private static void showTransactions() {
        Account account = findAccount(readLine("Account number: "));
        if (account == null) return;
        System.out.println("Transaction history for " + account.holder + " (" + account.number + "):");
        if (account.transactions.isEmpty()) {
            System.out.println("No transactions yet.");
            return;
        }
        for (Transaction transaction : account.transactions) {
            System.out.printf("%s | %-30s | %10s | Balance: %s%n",
                    transaction.time.format(TIME_FORMAT), transaction.description,
                    money(transaction.amount), money(transaction.balanceAfter));
        }
    }

    private static Account findAccount(String number) {
        Account account = ACCOUNTS.get(number.trim());
        if (account == null) System.out.println("Account not found.");
        return account;
    }

    private static BigDecimal readAmount(String prompt) {
        String input = readLine(prompt).trim();
        try {
            BigDecimal amount = new BigDecimal(input).setScale(2, RoundingMode.UNNECESSARY);
            if (amount.signum() <= 0) {
                System.out.println("Amount must be greater than zero.");
                return null;
            }
            return amount;
        } catch (NumberFormatException | ArithmeticException exception) {
            System.out.println("Enter a valid amount with no more than two decimal places.");
            return null;
        }
    }

    private static String readLine(String prompt) {
        System.out.print(prompt);
        return INPUT.nextLine();
    }

    private static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static final class Account {
        private final String number;
        private final String holder;
        private BigDecimal balance = BigDecimal.ZERO.setScale(2);
        private final List<Transaction> transactions = new ArrayList<>();

        private Account(String number, String holder) {
            this.number = number;
            this.holder = holder;
        }

        private void deposit(BigDecimal amount, String description) {
            balance = balance.add(amount);
            transactions.add(new Transaction(LocalDateTime.now(), description, amount, balance));
        }

        private void withdraw(BigDecimal amount, String description) {
            balance = balance.subtract(amount);
            transactions.add(new Transaction(LocalDateTime.now(), description, amount.negate(), balance));
        }
    }

    private record Transaction(LocalDateTime time, String description, BigDecimal amount,
                               BigDecimal balanceAfter) {}
}