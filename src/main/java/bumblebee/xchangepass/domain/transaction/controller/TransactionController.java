package bumblebee.xchangepass.domain.transaction.controller;

import bumblebee.xchangepass.domain.transaction.dto.cond.TransactionSearchCondition;
import bumblebee.xchangepass.domain.transaction.dto.response.TransactionResponse;
import bumblebee.xchangepass.domain.transaction.service.TransactionService;
import bumblebee.xchangepass.global.security.jwt.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;


    @Operation(summary = "거래내역 조회", description = "거래내역을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "400", description = "거래내역 조회 실패",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\n  \"code\": \"W001\"," +
                                                              "\n  \"message\": \"지갑을 찾을 수 없습니다.\"}"))
            )
    })
    @GetMapping("/v2/transaction")
    @ResponseStatus(HttpStatus.OK)
    public List<TransactionResponse> transaction(@AuthenticationPrincipal CustomUserDetails user,
                                                 @ModelAttribute TransactionSearchCondition condition,
                                                 int size) {
        return transactionService.getTransactionByMongo(user.getUserId(), condition, size);
    }

}
