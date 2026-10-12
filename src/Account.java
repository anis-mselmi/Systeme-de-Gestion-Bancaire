import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class Account {
    private final String number;
    private final String holder;
    private BigDecimal balance = BigDecimal.ZERO.setScale(2);
    private final List<Transaction> transactions = new ArrayList<>();

    Account(String number, String holder) {
        this.number = number;
        this.holder = holder;
    }

    public String number() {
        return number;
    }

    public String holder() {
        return holder;
    }

    public BigDecimal balance() {
        return balance;
    }

    public List<Transaction> transactions() {
        return List.copyOf(transactions);
    }

    void deposit(BigDecimal amount, String description) {
        balance = balance.add(amount);
        transactions.add(new Transaction(java.time.LocalDateTime.now(), description, amount, balance));
    }

    void withdraw(BigDecimal amount, String description) {
        balance = balance.subtract(amount);
        transactions.add(new Transaction(java.time.LocalDateTime.now(), description, amount.negate(), balance));
    }

    void restore(BigDecimal savedBalance, List<Transaction> savedTransactions) {
        balance = savedBalance;
        transactions.clear();
        transactions.addAll(savedTransactions);
    }

    @Override
    public String toString() {
        return number + " — " + holder + " (" + balance.setScale(2).toPlainString() + ")";
    }
}
