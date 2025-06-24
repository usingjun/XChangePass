package bumblebee.xchangepass.domain.wallet.wallet.controller;

import bumblebee.xchangepass.domain.wallet.wallet.dto.WalletPasswordRequest;
import bumblebee.xchangepass.domain.wallet.wallet.dto.WalletPasswordResponse;
import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletInOutRequest;
import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletTransferRequest;
import bumblebee.xchangepass.domain.wallet.wallet.dto.response.WalletBalanceResponse;
import bumblebee.xchangepass.domain.wallet.wallet.service.WalletServiceFactory;
import bumblebee.xchangepass.domain.wallet.wallet.service.impl.WalletFacadeService;
import bumblebee.xchangepass.domain.wallet.wallet.service.impl.WalletServiceImpl;
import bumblebee.xchangepass.global.security.jwt.CustomUserDetails;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/wallet")
@Tag(name = "Wallet", description = "Wallet CRUD API")
public class WalletController {

    private final WalletServiceFactory walletServiceFactory;
    private final WalletServiceImpl walletService;
    private final WalletFacadeService walletFacadeService;

    @Operation(summary = "잔액 충전", description = "잔액을 충전합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "충전 성공", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "400", description = "충전 실패",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\n  \"code\": \"W001\"," +
                                                              "\n  \"message\": \"지갑을 찾을 수 없습니다.\"}"))
            ),
            @ApiResponse(responseCode = "400", description = "사용자를 찾을 수 없음",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\n  \"code\": \"U001\"," +
                                                              "\n  \"message\": \"존재 하지 않는 회원입니다.\"}"))
            )
    })
    @PostMapping("/charge")
    @ResponseStatus(HttpStatus.CREATED)
    public void charge(@RequestBody @Valid WalletInOutRequest request,
                       @AuthenticationPrincipal CustomUserDetails user) {
        walletServiceFactory.getService("namedLock").charge(user.getUserId(), request);
    }

    @Operation(summary = "출금", description = "돈을 출금합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "출금 성공", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "400", description = "출금 실패",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\n  \"code\": \"B002\"," +
                                                              "\n  \"message\": \"충전 금액이 부족합니다.\"}"))
            )
    })
    @PutMapping("/withdraw")
    @ResponseStatus(HttpStatus.OK)
    public BigDecimal withdrawal(@RequestBody @Valid WalletInOutRequest request,
                                 @AuthenticationPrincipal CustomUserDetails user) {
        return walletServiceFactory.getService("namedLock").withdrawal(user.getUserId(), request);
    }

    @Operation(summary = "앱 내 송금", description = "돈을 송금합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "송금 성공", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "400", description = "먼저 충전이 필요합니다.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\n  \"code\": \"B001\"," +
                                                              "\n  \"message\": \"해당 화폐 잔액이 존재하지 않습니다.\"}"))
            ),
            @ApiResponse(responseCode = "400", description = "잔액이 부족합니다.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\n  \"code\": \"B002\"," +
                                                              "\n  \"message\": \"해당 화폐 잔액이 부족합니다.\"}"))
            )
    })
    @PutMapping("/transfer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void transfer(@RequestBody @Valid WalletTransferRequest request,
                         @AuthenticationPrincipal CustomUserDetails user) {
        walletFacadeService.transfer(user.getUserId(), request);
    }

    @Operation(summary = "앱 내 예약 송금", description = "돈을 송금합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "송금 성공", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "400", description = "먼저 충전이 필요합니다.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\n  \"code\": \"B001\"," +
                                                              "\n  \"message\": \"해당 화폐 잔액이 존재하지 않습니다.\"}"))
            ),
            @ApiResponse(responseCode = "400", description = "잔액이 부족합니다.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\n  \"code\": \"B002\"," +
                                                              "\n  \"message\": \"해당 화폐 잔액이 부족합니다.\"}"))
            )
    })
    @PutMapping("/transfer-schedule")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void scheduleTransfer(@RequestBody @Valid WalletTransferRequest request,
                         @AuthenticationPrincipal CustomUserDetails user) {
        walletFacadeService.transfer(user.getUserId(), request);
    }

    @Operation(summary = "잔액 조회", description = "잔액을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "잔액 조회 성공", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "400", description = "화폐 잔액이 존재하지 않습니다.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\n  \"code\": \"B001\"," +
                                                              "\n  \"message\": \"화폐 잔액이 존재하지 않습니다.\"}"))
            )
    })
    @GetMapping("/balance")
    @ResponseStatus(HttpStatus.OK)
    public List<WalletBalanceResponse> balance(@AuthenticationPrincipal CustomUserDetails user) {
        return walletServiceFactory.getService("namedLock").balance(user.getUserId());
    }


    @PostMapping("/verify-password")
    @ResponseStatus(HttpStatus.OK)
    public WalletPasswordResponse checkWalletPassword(@RequestBody WalletPasswordRequest request,
                                                      @AuthenticationPrincipal CustomUserDetails user) {
        return walletService.checkWalletPassword(user.getUserId(), request.password());
    }

}
