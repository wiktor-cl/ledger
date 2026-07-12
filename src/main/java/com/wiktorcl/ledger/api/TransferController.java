package com.wiktorcl.ledger.api;

import com.wiktorcl.ledger.api.dto.TransferRequest;
import com.wiktorcl.ledger.api.dto.TransferResponse;
import com.wiktorcl.ledger.service.TransferCommand;
import com.wiktorcl.ledger.service.TransferResult;
import com.wiktorcl.ledger.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.RoundingMode;

@RestController
@RequestMapping("/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> transfer(
            @Valid @RequestBody TransferRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        TransferCommand command = new TransferCommand(
                request.fromAccountId(),
                request.toAccountId(),
                request.amount().setScale(4, RoundingMode.HALF_UP),
                request.description(),
                idempotencyKey);
        TransferResult result = transferService.transfer(command);
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(TransferResponse.from(result));
    }
}
