import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BankService {
    private static final String FILE_HEADER = "BANKING_DATA_V1";
    private final Map<String, Account> accounts = new LinkedHashMap<>();
    private final Path dataFile;
    private int nextAccountNumber = 1001;
    private boolean persistenceEnabled = true;
    private String startupWarning;
    private String saveWarning;

    public BankService() {
        this(Path.of("bank-data.txt"));
    }

    BankService(Path dataFile) {
        this.dataFile = dataFile;
        loadData();
        if (persistenceEnabled && accounts.isEmpty()) {
            seedSampleData();
        }
    }

    /**
     * Populates the bank with realistic demo accounts the first time the application
     * runs (when no saved data file exists yet). The accounts are created through the
     * normal deposit/withdraw/transfer methods so every balance is backed by a real
     * transaction history, and the result is persisted to {@link #dataFile} like any
     * other change.
     */
    private void seedSampleData() {
        try {
            Account amine = createAccount("Amine Ben Salah");
            Account sonia = createAccount("Sonia Trabelsi");
            Account youssef = createAccount("Youssef Gharbi");
            Account leila = createAccount("Leila Haddad");
            Account karim = createAccount("Karim Bouazizi");

            deposit(amine.number(), new BigDecimal("4500.00"));
            deposit(sonia.number(), new BigDecimal("12850.75"));
            deposit(youssef.number(), new BigDecimal("980.50"));
            deposit(leila.number(), new BigDecimal("23000.00"));
            deposit(karim.number(), new BigDecimal("1500.00"));

            withdraw(amine.number(), new BigDecimal("320.00"));
            withdraw(leila.number(), new BigDecimal("1750.25"));
            withdraw(sonia.number(), new BigDecimal("600.00"));

            transfer(leila.number(), youssef.number(), new BigDecimal("2000.00"));
            transfer(sonia.number(), amine.number(), new BigDecimal("450.00"));
            transfer(karim.number(), youssef.number(), new BigDecimal("250.00"));
        } catch (RuntimeException exception) {
            // Seeding is best-effort; never let demo data block application startup.
            startupWarning = "Could not create sample accounts: " + exception.getMessage();
        }
    }

    public List<Account> accounts() {
        return List.copyOf(accounts.values());
    }

    public Account account(String number) {
        Account account = accounts.get(number == null ? "" : number.trim());
        if (account == null) throw new IllegalArgumentException("Account not found.");
        return account;
    }

    public Account createAccount(String holder) {
        String normalizedName = holder == null ? "" : holder.trim();
        if (normalizedName.isEmpty()) throw new IllegalArgumentException("Name cannot be empty.");
        String number = String.valueOf(nextAccountNumber++);
        Account account = new Account(number, normalizedName);
        accounts.put(number, account);
        saveData();
        return account;
    }

    public void deposit(String number, BigDecimal amount) {
        Account account = account(number);
        validateAmount(amount);
        account.deposit(amount, "Deposit");
        saveData();
    }

    public void withdraw(String number, BigDecimal amount) {
        Account account = account(number);
        validateAmount(amount);
        ensureFunds(account, amount);
        account.withdraw(amount, "Withdrawal");
        saveData();
    }

    public void transfer(String sourceNumber, String destinationNumber, BigDecimal amount) {
        Account source = account(sourceNumber);
        Account destination = account(destinationNumber);
        if (source == destination) throw new IllegalArgumentException("Choose two different accounts.");
        validateAmount(amount);
        ensureFunds(source, amount);
        source.withdraw(amount, "Transfer to account " + destination.number());
        destination.deposit(amount, "Transfer from account " + source.number());
        saveData();
    }

    public BigDecimal totalBalance() {
        return accounts.values().stream().map(Account::balance)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    public int transactionCount() {
        return accounts.values().stream().mapToInt(account -> account.transactions().size()).sum();
    }

    public String startupWarning() {
        return startupWarning;
    }

    public String saveWarning() {
        return saveWarning;
    }

    public Path dataFile() {
        return dataFile.toAbsolutePath();
    }

    public void exportStatement(Account account, Path destination) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("time,description,amount,balance_after");
        for (Transaction transaction : account.transactions()) {
            lines.add(String.join(",", transaction.time().toString(), csv(transaction.description()),
                    money(transaction.amount()), money(transaction.balanceAfter())));
        }
        Files.write(destination, lines, StandardCharsets.UTF_8);
    }

    private static void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2) {
            throw new IllegalArgumentException("Enter a positive amount with no more than two decimal places.");
        }
    }

    private static void ensureFunds(Account account, BigDecimal amount) {
        if (account.balance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient funds. Current balance: " + money(account.balance()));
        }
    }

    private void loadData() {
        if (!Files.exists(dataFile)) return;
        Map<String, Account> loaded = new LinkedHashMap<>();
        Map<String, List<Transaction>> loadedTransactions = new LinkedHashMap<>();
        Map<String, BigDecimal> savedBalances = new LinkedHashMap<>();
        int highestNumber = 1000;
        try {
            List<String> lines = Files.readAllLines(dataFile, StandardCharsets.UTF_8);
            if (lines.isEmpty() || !FILE_HEADER.equals(lines.get(0))) {
                throw new IOException("Unsupported or invalid data file format.");
            }
            for (int index = 1; index < lines.size(); index++) {
                String[] fields = lines.get(index).split("\\t", -1);
                if (fields[0].equals("A") && fields.length == 4) {
                    String number = fields[1];
                    String holder = decode(fields[2]);
                    BigDecimal balance = new BigDecimal(fields[3]).setScale(2, RoundingMode.UNNECESSARY);
                    if (!number.matches("\\d+") || holder.isBlank() || balance.signum() < 0
                            || loaded.containsKey(number)) {
                        throw new IOException("Invalid account record at line " + (index + 1));
                    }
                    Account account = new Account(number, holder);
                    loaded.put(number, account);
                    loadedTransactions.put(number, new ArrayList<>());
                    savedBalances.put(number, balance);
                    highestNumber = Math.max(highestNumber, Integer.parseInt(number));
                } else if (fields[0].equals("T") && fields.length == 6) {
                    List<Transaction> accountTransactions = loadedTransactions.get(fields[1]);
                    if (accountTransactions == null) throw new IOException("Transaction refers to an unknown account.");
                    accountTransactions.add(new Transaction(LocalDateTime.parse(fields[2]), decode(fields[3]),
                            new BigDecimal(fields[4]).setScale(2, RoundingMode.UNNECESSARY),
                            new BigDecimal(fields[5]).setScale(2, RoundingMode.UNNECESSARY)));
                } else {
                    throw new IOException("Invalid record at line " + (index + 1));
                }
            }

            for (Account account : loaded.values()) {
                List<Transaction> history = loadedTransactions.get(account.number());
                BigDecimal balance = BigDecimal.ZERO.setScale(2);
                for (Transaction transaction : history) {
                    balance = balance.add(transaction.amount());
                    if (balance.compareTo(transaction.balanceAfter()) != 0) {
                        throw new IOException("Transaction history is inconsistent for account "
                                + account.number() + ".");
                    }
                }
                if (balance.compareTo(savedBalances.get(account.number())) != 0) {
                    throw new IOException("Transaction history does not match account " + account.number() + ".");
                }
                account.restore(balance, history);
            }
            accounts.putAll(loaded);
            nextAccountNumber = highestNumber + 1;
        } catch (IOException | IllegalArgumentException exception) {
            persistenceEnabled = false;
            startupWarning = "Could not load " + dataFile + ": " + exception.getMessage()
                    + " The existing file will be left untouched.";
        }
    }

    private boolean saveData() {
        if (!persistenceEnabled) {
            saveWarning = startupWarning;
            return false;
        }
        Path absoluteFile = dataFile.toAbsolutePath();
        Path temporaryFile = absoluteFile.resolveSibling(absoluteFile.getFileName() + ".tmp");
        try {
            List<String> lines = new ArrayList<>();
            lines.add(FILE_HEADER);
            for (Account account : accounts.values()) {
                lines.add(String.join("\t", "A", account.number(), encode(account.holder()), money(account.balance())));
                for (Transaction transaction : account.transactions()) {
                    lines.add(String.join("\t", "T", account.number(), transaction.time().toString(),
                            encode(transaction.description()), money(transaction.amount()), money(transaction.balanceAfter())));
                }
            }
            Files.write(temporaryFile, lines, StandardCharsets.UTF_8);
            try {
                Files.move(temporaryFile, absoluteFile, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile, absoluteFile, StandardCopyOption.REPLACE_EXISTING);
            }
            saveWarning = null;
            return true;
        } catch (IOException exception) {
            saveWarning = "Changes could not be saved: " + exception.getMessage();
            try {
                Files.deleteIfExists(temporaryFile);
            } catch (IOException ignored) {
                // Leave the original data file intact if temporary-file cleanup fails.
            }
            return false;
        }
    }

    private static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static String csv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
