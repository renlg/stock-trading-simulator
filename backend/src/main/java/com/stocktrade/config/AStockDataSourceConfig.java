package com.stocktrade.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

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
        // 行情引擎每2秒高频查询, 用 HikariCP 池化只读连接避免频繁建连。
        // 无参构造为懒初始化: 数据文件缺失时与 DriverManagerDataSource 一样在首次使用才报错, 不影响启动。
        // 注意: 不能调 setReadOnly(true) —— sqlite-jdbc 连接建立后调用 setReadOnly 会直接抛异常,
        // 只读保证由 URL 的 mode=ro + connectionInitSql 的 query_only 共同承担。
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName("a-stock-ro");
        ds.setDriverClassName("org.sqlite.JDBC");
        ds.setJdbcUrl("jdbc:sqlite:file:" + A_STOCK_DB + "?mode=ro");
        ds.setMaximumPoolSize(5);
        ds.setConnectionInitSql("PRAGMA query_only = true");
        return new JdbcTemplate(ds);
    }
}
