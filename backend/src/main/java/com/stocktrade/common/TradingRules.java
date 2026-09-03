package com.stocktrade.common;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * A股交易规则公共工具:
 * - 涨跌停幅度判断(主板10%/创业板科创板20%/ST股5%)
 * - 手续费计算(佣金0.025%最低5元, 印花税0.05%仅卖出)
 * - 下一个交易日计算(简单算法: +1自然日, 周末顺延)
 */
public final class TradingRules {
    private TradingRules() {}

    private static final double COMMISSION_RATE = 0.00025;
    private static final double MIN_COMMISSION = 5.0;
    private static final double STAMP_DUTY_RATE = 0.0005;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * 根据股票代码判断涨跌停幅度
     * - 300/301开头: 创业板 20%
     * - 688/689开头: 科创板 20%
     * - 名称含ST: 5%
     * - 其他(主板): 10%
     */
    public static double getLimitPct(String code, String name) {
        if (code != null) {
            if (code.startsWith("300") || code.startsWith("301")
                    || code.startsWith("688") || code.startsWith("689")) {
                return 0.20;
            }
        }
        if (name != null && name.contains("ST")) {
            return 0.05;
        }
        return 0.10;
    }

    /** 计算买入佣金: 成交额×0.025%, 最低5元 */
    public static double calcCommission(double amount) {
        return Math.max(amount * COMMISSION_RATE, MIN_COMMISSION);
    }

    /** 计算卖出印花税: 成交额×0.05% */
    public static double calcStampDuty(double amount) {
        return amount * STAMP_DUTY_RATE;
    }

    /**
     * 计算下一个交易日(简单算法: +1自然日, 周末顺延, 忽略节假日)
     * 用于T+1规则: 当天买入的股票, 最早下一个交易日才能卖
     */
    public static String nextTradingDay() {
        LocalDate today = LocalDate.now();
        LocalDate next = today.plusDays(1);
        DayOfWeek dow = next.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY) {
            next = next.plusDays(2);
        } else if (dow == DayOfWeek.SUNDAY) {
            next = next.plusDays(1);
        }
        return next.format(DATE_FMT);
    }

    /** 判断今天是否 >= availableDate (T+1校验) */
    public static boolean canSell(String availableDate) {
        if (availableDate == null || availableDate.isBlank()) return true;
        LocalDate today = LocalDate.now();
        LocalDate available = LocalDate.parse(availableDate, DATE_FMT);
        return !today.isBefore(available);
    }
}
