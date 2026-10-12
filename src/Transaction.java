import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Transaction(LocalDateTime time, String description, BigDecimal amount,
                         BigDecimal balanceAfter) {}
