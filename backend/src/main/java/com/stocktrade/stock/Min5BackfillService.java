package com.stocktrade.stock;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** 添加自选股后，异步补充该股票的历史 5 分钟线。 */
@Service
public class Min5BackfillService {
    private static final Logger log = LoggerFactory.getLogger(Min5BackfillService.class);
    private static final long TIMEOUT_SECONDS = 60;

    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(10),
            daemonThreadFactory(),
            new ThreadPoolExecutor.AbortPolicy());

    /** 提交回填任务；提交或执行失败均不影响添加自选股。 */
    public void backfillAsync(String code) {
        try {
            executor.execute(() -> backfill(code));
        } catch (RuntimeException e) {
            log.warn("提交 5 分钟线回填任务失败, code={}", code, e);
        }
    }

    private void backfill(String code) {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{
                    "/usr/bin/python3", "/opt/a-stock/backfill_min5.py", "--code", code
            });
            boolean completed = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                log.warn("5 分钟线回填超时, code={}", code);
                return;
            }

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0) {
                log.warn("5 分钟线回填失败, code={}, exitCode={}, output={}", code, process.exitValue(), output);
                return;
            }
            log.info("5 分钟线回填完成, code={}, output={}", code, output);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("5 分钟线回填被中断, code={}", code, e);
        } catch (IOException | RuntimeException e) {
            log.warn("5 分钟线回填执行失败, code={}", code, e);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private static ThreadFactory daemonThreadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "min5-backfill");
            thread.setDaemon(true);
            return thread;
        };
    }
}
