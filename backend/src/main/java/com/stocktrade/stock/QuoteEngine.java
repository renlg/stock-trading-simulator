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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 行情引擎: 从 /opt/a-stock 真实数据(最新分钟线 + 昨日日线)驱动关注池行情。
 * 不再随机游走 —— price 来自真实分钟线收盘, prev_close 来自真实昨日收盘,
 * 涨跌幅 = (真实最新价 - 真实昨收) / 真实昨收, 不会出现 18% 式荒谬涨幅。
 * 每 tick 重读一次库, 盘中工作流26每5分钟更新分钟线, 引擎下一tick即可拿到最新价。
 */
@Component
public class QuoteEngine {
    private static final Logger log = LoggerFactory.getLogger(QuoteEngine.class);

    private final JdbcTemplate jdbc;
    private final StockService stocks;
    private final StockPoolService pool;
    private final ObjectProvider<ConditionService> conditions;
    private final int persistTicks;
    private final AtomicInteger ticks = new AtomicInteger();

    public QuoteEngine(JdbcTemplate jdbc, StockService stocks, StockPoolService pool,
                       ObjectProvider<ConditionService> conditions,
                       @Value("${stock.quote.persist-ticks:30}") int persistTicks) {
        this.jdbc = jdbc;
        this.stocks = stocks;
        this.pool = pool;
        this.conditions = conditions;
        this.persistTicks = persistTicks;
    }

    @PostConstruct
    public void initialize() {
        // 默认精选池: 空时从A股市场自动填充(可按配置覆盖数量)
        int defaultPool = 30;
        try {
            defaultPool = Integer.parseInt(System.getProperty("stock.pool.size", "30"));
        } catch (NumberFormatException ignored) {}
        pool.ensureDefaultPool(defaultPool);

        // 用真实数据初始化内存行情
        refreshFromRealData();
        stocks.reload();
        log.info("行情引擎已载入{}只真实行情(来源: /opt/a-stock)", stocks.all().size());
    }

    @Scheduled(fixedDelayString = "${stock.quote.interval-ms:2000}")
    public void scheduledUpdate() {
        updatePricesOnce();
    }

    public void updatePricesOnce() {
        refreshFromRealData();
        if (ticks.incrementAndGet() % Math.max(1, persistTicks) == 0) persist();
        ConditionService service = conditions.getIfAvailable();
        if (service != null) service.checkAndTrigger();
    }

    /** 从 /opt/a-stock 读关注池每只最新分钟线+昨收, 更新内存行情 */
    private void refreshFromRealData() {
        List<Map<String, Object>> watch = pool.watchList();
        String now = AuthService.now();
        // 同步池: 移除已不在 watch_stocks 的行情(支持"删除股票"后立即从列表消失)
        java.util.Set<String> active = new java.util.HashSet<>();
        for (Map<String, Object> w : watch) active.add((String) w.get("code"));
        for (StockQuote q : stocks.all()) {
            if (!active.contains(q.code())) stocks.remove(q.code());
        }
        for (Map<String, Object> w : watch) {
            String code = (String) w.get("code");
            String name = (String) w.get("name");
            try {
                Map<String, Object> q = pool.realQuote(code);
                if (q == null) continue; // 无分钟线数据, 跳过
                double price = ((Number) q.get("price")).doubleValue();
                double prevClose = ((Number) q.get("prevClose")).doubleValue();
                double high = ((Number) q.get("high")).doubleValue();
                double low = ((Number) q.get("low")).doubleValue();
                // 取引擎内已有 high/low 做历史最高/最低(分钟线 high/low 是当根K线内的)
                StockQuote old = stocks.getOrNull(code);
                double histHigh = old != null ? Math.max(old.high(), high) : high;
                double histLow = old != null ? Math.min(old.low(), low) : low;
                stocks.put(new StockQuote(code, name, prevClose, price, histHigh, histLow, now));
            } catch (Exception e) {
                log.warn("刷新行情失败 code={}: {}", code, e.getMessage());
            }
        }
    }

    public void persist() {
        List<StockQuote> snapshot = stocks.all();
        if (snapshot.isEmpty()) return;
        jdbc.batchUpdate("INSERT OR REPLACE INTO stocks(code,name,prev_close,price,high,low,updated_at) VALUES(?,?,?,?,?,?,?)", snapshot,
                snapshot.size(), (ps, quote) -> {
                    ps.setString(1, quote.code()); ps.setString(2, quote.name());
                    ps.setDouble(3, quote.prevClose()); ps.setDouble(4, quote.price());
                    ps.setDouble(5, quote.high()); ps.setDouble(6, quote.low());
                    ps.setString(7, quote.updatedAt());
                });
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
