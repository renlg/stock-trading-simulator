package com.stocktrade.backtest;

import com.stocktrade.common.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/backtest")
public class BacktestController {

    private final BacktestService backtestService;

    public BacktestController(BacktestService backtestService) {
        this.backtestService = backtestService;
    }

    @GetMapping("/strategies")
    public Result<List<Map<String, Object>>> strategies() {
        return Result.success(backtestService.listStrategies());
    }

    @PostMapping("/run")
    public Result<Map<String, Object>> run(@RequestBody BacktestRequest req) {
        return Result.success(backtestService.runBacktest(
                req.code(), req.strategy(), req.params(),
                req.startDate(), req.endDate(),
                req.initialCapital() != null ? req.initialCapital() : 100000));
    }

    public record BacktestRequest(
            String code,
            String strategy,
            Map<String, Object> params,
            String startDate,
            String endDate,
            Double initialCapital
    ) {
    }
}
