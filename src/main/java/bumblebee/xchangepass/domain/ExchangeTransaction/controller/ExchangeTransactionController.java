package bumblebee.xchangepass.domain.ExchangeTransaction.controller;

import bumblebee.xchangepass.domain.ExchangeTransaction.dto.request.ExchangeRequestDTO;
import bumblebee.xchangepass.domain.ExchangeTransaction.dto.response.ExchangeResponseDTO;
import bumblebee.xchangepass.domain.ExchangeTransaction.service.ExchangeTransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/api/exchange")
@RestController
@RequiredArgsConstructor
@Tag(name = "ExchangeTransaction", description = "ExchangeTransaction management API")
public class ExchangeTransactionController {

    private final ExchangeTransactionService exchangeService;

    @Operation(summary = "환전 생성 하기", description = "환전 생성시 환전시 발생하는 내역들")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "404", description = "존재 하지 않는 환율",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\n  \"code\": \"E001\"," +
                                    "\n  \"message\": \"존재 하지 않는 환율입니다.\"\n}"))
            ),
            @ApiResponse(responseCode = "400", description = "환전 금액 미입력",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\n  \"code\": \"T003\"," +
                                    "\n  \"message\": \"환전 금액은 필수입니다.\"\n}"))
            ),
    })
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/create")
    public ExchangeResponseDTO createTransaction(@RequestBody ExchangeRequestDTO request) {
        return exchangeService.createTransaction(request);
    }


    @Operation(summary = "환전 하기", description = "환전을 실행 합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "404", description = "존재 하지않는 환전 내역입니다.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\n  \"code\": \"T002\"," +
                                    "\n  \"message\": \"회원가입에 실패했습니다.\"," +
                                    "\n  \"validation\": {\n    \"email\": \"존재 하지않는 환전 내역입니다.\"\n  }\n}"))
            ),
            @ApiResponse(responseCode = "400", description = "거래 완료된 내역",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\n  \"code\": \"T001\"," +
                                    "\n  \"message\": \"이미 완료된 거래입니다.\"}"))
            )
    })
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/execute")
    public ExchangeResponseDTO executeTransaction(@RequestParam Long transactionId) {
        return exchangeService.executeTransaction(transactionId);
    }
}
