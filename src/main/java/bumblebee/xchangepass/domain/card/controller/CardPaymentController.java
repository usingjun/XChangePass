package bumblebee.xchangepass.domain.card.controller;

import bumblebee.xchangepass.domain.card.dto.request.PaymentRequest;
import bumblebee.xchangepass.domain.card.dto.response.PaymentResponse;
import bumblebee.xchangepass.domain.card.service.CardPaymentService;
import bumblebee.xchangepass.global.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/card/payment")
@Tag(name = "Card Payment", description = "카드 결제 API")
public class CardPaymentController {

    private final CardPaymentService cardPaymentService;

    @Operation(summary = "카드 결제 요청", description = "카드 정보를 검증하고 결제를 처리합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "결제 성공"),
            @ApiResponse(responseCode = "400", description = "결제 실패",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorCode.class),
                            examples = @ExampleObject(value = "{\n  \"code\": \"B001\"," +
                                    "\n  \"message\": \"잔액이 부족합니다.\"\n}"))
            )
    })
    @ResponseStatus(HttpStatus.OK)
    @PostMapping
    public PaymentResponse processPayment(@RequestBody @Valid PaymentRequest request) {
        return cardPaymentService.processPayment(request);
    }
}
