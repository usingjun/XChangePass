package bumblebee.xchangepass.domain.cardTransaction.controller;

import bumblebee.xchangepass.domain.cardTransaction.dto.response.CardTransactionDetailResponse;
import bumblebee.xchangepass.domain.cardTransaction.dto.response.CardTransactionSummaryResponse;
import bumblebee.xchangepass.domain.cardTransaction.service.CardTransactionService;
import bumblebee.xchangepass.global.common.CursorResponse;
import bumblebee.xchangepass.global.security.jwt.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/card/transactions")
@Tag(name = "CardTransaction", description = "카드 거래내역 관련 API")
public class CardTransactionController {

    private final CardTransactionService cardTransactionService;

    @Operation(summary = "거래내역 무한 스크롤 조회", description = "사용자의 카드 거래내역을 최신순으로 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "400", description = "조회 실패", content = @Content(mediaType = "application/json",
                    examples = @ExampleObject(value = "{\n  \"code\": \"T001\",\n  \"message\": \"거래내역을 찾을 수 없습니다.\"\n}")))
    })
    @ResponseStatus(HttpStatus.OK)
    @GetMapping
    public CursorResponse<CardTransactionSummaryResponse> getTransactions(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) Long lastTransactionId,
            @RequestParam(defaultValue = "10") int size) {

        List<CardTransactionSummaryResponse> transactions =
                cardTransactionService.getUserTransactions(user.getUserId(), lastTransactionId, size);

        Long nextCursor = transactions.isEmpty() ? null :
                transactions.get(transactions.size() - 1).transactionId();

        return CursorResponse.of(transactions, nextCursor);
    }

    @Operation(summary = "거래내역 상세 조회", description = "사용자의 특정 거래내역을 상세 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "404", description = "거래내역 없음", content = @Content(mediaType = "application/json",
                    examples = @ExampleObject(value = "{\n  \"code\": \"T002\",\n  \"message\": \"해당 거래내역을 찾을 수 없습니다.\"\n}")))
    })
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/{transactionId}")
    public CardTransactionDetailResponse getTransactionDetail(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long transactionId) {

        return cardTransactionService.getTransactionDetail(user.getUserId(), transactionId);
    }
}
