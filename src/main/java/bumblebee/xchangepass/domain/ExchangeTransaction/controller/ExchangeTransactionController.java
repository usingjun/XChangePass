package bumblebee.xchangepass.domain.ExchangeTransaction.controller;

import bumblebee.xchangepass.domain.ExchangeTransaction.dto.request.ExchangeRequestDTO;
import bumblebee.xchangepass.domain.ExchangeTransaction.dto.response.ExchangeResponseDTO;
import bumblebee.xchangepass.domain.ExchangeTransaction.service.ExchangeTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/api/exchange")
@RestController
@RequiredArgsConstructor
public class ExchangeTransactionController {

    private final ExchangeTransactionService exchangeService;

    // 환전시 나오는 환율 및 환전 금액
    @PostMapping("/create")
    public ExchangeResponseDTO createTransaction(@RequestBody ExchangeRequestDTO request) {
        return exchangeService.createTransaction(request);
    }
    // 환전 실행
    @PostMapping("/execute")
    public ExchangeResponseDTO executeTransaction(@RequestParam Long transactionId) {
        return exchangeService.executeTransaction(transactionId);
    }
}
