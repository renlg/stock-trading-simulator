package com.stocktrade.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

/**
 * A股真实数据源: 只读连接 /opt/a-stock/data/stock.db (工作流26每日同步分钟线/日线的库)。
 * 模拟盘行情从这里读真实数据, 不自己造随机数据。
 *
 * 关键: 显式定义主库 JdbcTemplate 并标 @Primary, 只读 JdbcTemplate 用 @Qualifier("aStockJdbc") 取。
 * 这样所有无 @Qualifier 的 JdbcTemplate 注入都拿到主库(模拟盘自己的 data/stock.db),
 * 而 StockPoolService 用 @Qualifier("aStockJdbc") 读真实数据。
 */
@Configuration
public class AStockDataSourceConfig {

    /** 生产环境: /opt/a-stock/data/stock.db; 本地开发可覆盖 -Dastock.db=路径 */
    public static final String A_STOCK_DB = System.getProperty("astock.db", "/opt/a-stock/data/stock.db");

    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "aStockJdbc")
    public JdbcTemplate aStockJdbc() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.sqlite.JDBC");
        ds.setUrl("jdbc:sqlite:file:" + A_STOCK_DB + "?mode=ro");
        return new JdbcTemplate(ds);
    }
}
