package com.stocktrade.stock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 真实行情服务: 从新浪 hq.sinajs.cn 拉取实时价格, 用于买卖成交价。
 * 失败时返回 null/-1, 由调用方降级到本地模拟价, 保证交易不中断。
 */
@Service
public class RealTimeQuoteService {
    private static final Logger log = LoggerFactory.getLogger(RealTimeQuoteService.class);
    private static final String SINA_URL = "https://hq.sinajs.cn/list=";
    private static final String REFERER = "https://finance.sina.com.cn/";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/126.0.0.0 Safari/537.36";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public record RealtimeQuote(double price, double prevClose, double volume) {}

    /** 拉取单只股票完整实时行情。失败返回 null。 */
    public RealtimeQuote fetchQuote(String code) {
        try {
            String sym = marketPrefix(code) + code;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(SINA_URL + sym))
                    .header("Referer", REFERER)
                    .header("User-Agent", UA)
                    .timeout(Duration.ofSeconds(8))
                    .GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return null;
            String body = resp.body();
            // var hq_str_sh600519="贵州茅台,今开,昨收,现价,最高,最低,买一,卖一,成交量,...";
            int q1 = body.indexOf('"');
            int q2 = body.lastIndexOf('"');
            if (q1 < 0 || q2 <= q1) return null;
            String[] parts = body.substring(q1 + 1, q2).split(",");
            if (parts.length < 9) return null;
            double price = Double.parseDouble(parts[3]);
            double prevClose = Double.parseDouble(parts[2]);
            double volume = Double.parseDouble(parts[8]);
            return new RealtimeQuote(price, prevClose, volume);
        } catch (Exception e) {
            log.warn("实时行情拉取失败 code={}: {}", code, e.getMessage());
            return null;
        }
    }

    /** 拉取单只股票实时价(元)。失败返回 -1。 */
    public double fetchPrice(String code) {
        RealtimeQuote q = fetchQuote(code);
        return q != null ? q.price() : -1;
    }

    private static String marketPrefix(String code) {
        return code.startsWith("6") || code.startsWith("9") ? "sh" : "sz";
    }
}
