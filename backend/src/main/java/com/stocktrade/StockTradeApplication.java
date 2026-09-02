package com.stocktrade;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@EnableScheduling
@SpringBootApplication
public class StockTradeApplication {
    public static void main(String[] args) throws IOException {
        // SQLite 不会自动创建父目录，因此在数据源初始化前准备目录。
        Files.createDirectories(Path.of("data"));
        SpringApplication.run(StockTradeApplication.class, args);
    }
}
