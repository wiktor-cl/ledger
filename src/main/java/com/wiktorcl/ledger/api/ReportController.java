package com.wiktorcl.ledger.api;

import com.wiktorcl.ledger.api.dto.AccountStatementResponse;
import com.wiktorcl.ledger.api.dto.CategoryTurnoverResponse;
import com.wiktorcl.ledger.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/accounts/{id}/statement")
    public AccountStatementResponse statement(
            @PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return AccountStatementResponse.from(reportService.statement(id, from, to));
    }

    @GetMapping("/turnover")
    public List<CategoryTurnoverResponse> turnover(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return reportService.turnoverByCategory(from, to).stream().map(CategoryTurnoverResponse::from).toList();
    }
}
