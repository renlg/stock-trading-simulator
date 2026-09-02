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
 * 失败时返回 -1, 由调用方降级到本地模拟价, 保证交易不中断。
 */
@Service
public class RealTimeQuoteService {
    private static final Logger log = LoggerFactory.getLogger(RealTimeQuoteService.class);
    private static final String SINA_URL = "https://hq.sinajs.cn/list=";
    private static final String REFERER = "https://finance.sina.com.cn/";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/126.0.0.0 Safari/537.36";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    /** 拉取单只股票实时价(元)。失败返回 -1。 */
    public double fetchPrice(String code) {
        try {
            String sym = marketPrefix(code) + code;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(SINA_URL + sym))
                    .header("Referer", REFERER)
                    .header("User-Agent", UA)
                    .timeout(Duration.ofSeconds(8))
                    .GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return -1;
            String body = resp.body();
            // var hq_str_sh600519="贵州茅台,今开,昨收,现价,最高,最低,买一,卖一,...";
            int q1 = body.indexOf('"');
            int q2 = body.lastIndexOf('"');
            if (q1 < 0 || q2 <= q1) return -1;
            String[] parts = body.substring(q1 + 1, q2).split(",");
            if (parts.length < 4) return -1;
            return Double.parseDouble(parts[3]); // 第4个字段=当前价
        } catch (Exception e) {
            log.warn("实时行情拉取失败 code={}: {}", code, e.getMessage());
            return -1;
        }
    }

    private static String marketPrefix(String code) {
        return code.startsWith("6") || code.startsWith("9") ? "sh" : "sz";
    }
}
