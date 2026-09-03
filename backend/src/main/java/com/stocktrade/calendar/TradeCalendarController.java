package com.stocktrade.calendar;

import com.stocktrade.common.Result;
import org.springframework.web.bind.annotation.*;

import java.time.Year;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/calendar")
public class TradeCalendarController {

    private final TradeCalendarService calendarService;

    public TradeCalendarController(TradeCalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @GetMapping
    public Result<List<String>> list(@RequestParam(required = false) Integer year) {
        int thisYear = Year.now().getValue();
        int startYear = year != null ? year : thisYear;
        int endYear = year != null ? year : thisYear + 1;
        return Result.success(calendarService.listTradeDays(startYear, endYear));
    }

    @PostMapping("/holidays")
    public Result<Void> markHoliday(@RequestBody Map<String, Object> body) {
        String date = (String) body.get("date");
        Boolean isHoliday = (Boolean) body.get("isHoliday");
        if (date == null || isHoliday == null) {
            return Result.failure(400, "参数不完整");
        }
        String note = (String) body.getOrDefault("note", null);
        calendarService.markHoliday(date, isHoliday, note);
        return Result.success();
    }
}
