package com.wrongquestion.backend.ai;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.*;
import java.time.Clock;
import java.time.LocalDate;

/** Called inside the short transaction which creates a call record, before HTTP is sent. */
@Service
public class AiUsageService {
    private final JdbcTemplate db;
    private final AiProperties properties;
    private final Clock clock;

    public AiUsageService(JdbcTemplate db, AiProperties properties, Clock clock) {
        this.db = db;
        this.properties = properties;
        this.clock = clock;
    }

    public void reserve() {
        var today = LocalDate.now(clock);
        db.update(
                "INSERT INTO ai_daily_usage(business_date,call_count) VALUES (?,0) ON DUPLICATE KEY"
                    + " UPDATE call_count=ai_daily_usage.call_count",
                today);
        if (db.update(
                        "UPDATE ai_daily_usage SET call_count=call_count+1 WHERE business_date=?"
                            + " AND call_count<?",
                        today,
                        properties.dailyCallLimit())
                == 0) throw new AiException("AI_DAILY_LIMIT", "今日 AI 执行额度已用完，未发出新的模型调用", 429);
    }

    public static BigDecimal estimate(
            Long input, Long output, BigDecimal inputPrice, BigDecimal outputPrice) {
        if (input == null || output == null || inputPrice == null || outputPrice == null)
            return null;
        return inputPrice
                .multiply(BigDecimal.valueOf(input))
                .add(outputPrice.multiply(BigDecimal.valueOf(output)))
                .divide(BigDecimal.valueOf(1_000_000), 8, RoundingMode.HALF_UP);
    }
}
