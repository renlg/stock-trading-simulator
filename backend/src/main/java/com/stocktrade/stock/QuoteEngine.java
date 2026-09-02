package com.stocktrade.stock;

import com.stocktrade.auth.AuthService;
import com.stocktrade.condition.ConditionService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class QuoteEngine {
    private static final Logger log = LoggerFactory.getLogger(QuoteEngine.class);
    private static final List<Seed> SEEDS = List.of(
            new Seed("600519", "贵州茅台", 1700.00), new Seed("000001", "平安银行", 10.50),
            new Seed("600036", "招商银行", 34.20), new Seed("601318", "中国平安", 45.60),
            new Seed("000858", "五粮液", 138.00), new Seed("600276", "恒瑞医药", 43.50),
            new Seed("000333", "美的集团", 62.80), new Seed("600900", "长江电力", 25.30),
            new Seed("601398", "工商银行", 5.40), new Seed("601857", "中国石油", 9.20),
            new Seed("600030", "中信证券", 19.80), new Seed("002594", "比亚迪", 225.00),
            new Seed("300750", "宁德时代", 190.00), new Seed("601012", "隆基绿能", 20.10),
            new Seed("600887", "伊利股份", 27.60), new Seed("000651", "格力电器", 38.40),
            new Seed("601088", "中国神华", 39.50), new Seed("600028", "中国石化", 6.30),
            new Seed("601166", "兴业银行", 16.80), new Seed("600050", "中国联通", 4.70),
            new Seed("002415", "海康威视", 31.20), new Seed("600309", "万华化学", 82.00),
            new Seed("603288", "海天味业", 38.60), new Seed("000725", "京东方A", 4.10)
    );

    private final JdbcTemplate jdbc;
    private final StockService stocks;
    private final ObjectProvider<ConditionService> conditions;
    private final int persistTicks;
    private final AtomicInteger ticks = new AtomicInteger();

    public QuoteEngine(JdbcTemplate jdbc, StockService stocks, ObjectProvider<ConditionService> conditions,
                       @Value("${stock.quote.persist-ticks:30}") int persistTicks) {
        this.jdbc = jdbc;
        this.stocks = stocks;
        this.conditions = conditions;
        this.persistTicks = persistTicks;
    }

    @PostConstruct
    public void initialize() {
        String now = AuthService.now();
        for (Seed seed : SEEDS) {
            jdbc.update("INSERT OR IGNORE INTO stocks(code,name,prev_close,price,high,low,updated_at) VALUES(?,?,?,?,?,?,?)",
                    seed.code(), seed.name(), seed.price(), seed.price(), seed.price(), seed.price(), now);
        }
        stocks.reload();
        log.info("模拟行情引擎已载入{}只股票", stocks.all().size());
    }

    @Scheduled(fixedDelayString = "${stock.quote.interval-ms:2000}")
    public void scheduledUpdate() {
        updatePricesOnce();
    }

    public void updatePricesOnce() {
        String now = AuthService.now();
        for (StockQuote old : stocks.all()) {
            double factor = 1 + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.01;
            double price = round(Math.max(0.01, old.price() * factor));
            stocks.put(new StockQuote(old.code(), old.name(), old.prevClose(), price,
                    Math.max(old.high(), price), Math.min(old.low(), price), now));
        }
        if (ticks.incrementAndGet() % Math.max(1, persistTicks) == 0) persist();
        ConditionService service = conditions.getIfAvailable();
        if (service != null) service.checkAndTrigger();
    }

    public void persist() {
        List<StockQuote> snapshot = stocks.all();
        jdbc.batchUpdate("UPDATE stocks SET price=?,high=?,low=?,updated_at=? WHERE code=?", snapshot,
                snapshot.size(), (ps, quote) -> {
                    ps.setDouble(1, quote.price()); ps.setDouble(2, quote.high());
                    ps.setDouble(3, quote.low()); ps.setString(4, quote.updatedAt()); ps.setString(5, quote.code());
                });
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private record Seed(String code, String name, double price) {}
}
