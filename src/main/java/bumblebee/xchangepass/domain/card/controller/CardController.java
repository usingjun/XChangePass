package bumblebee.xchangepass.domain.card.controller;

import bumblebee.xchangepass.domain.card.dto.request.ChangeCardStatusRequest;
import bumblebee.xchangepass.domain.card.dto.response.BasicCardInfoResponse;
import bumblebee.xchangepass.domain.card.dto.response.DetailedCardInfoResponse;
import bumblebee.xchangepass.domain.card.service.CardService;
import bumblebee.xchangepass.domain.user.dto.CustomUserDetails;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequestMapping("/api/v1/card")
@RestController
@RequiredArgsConstructor
@Tag(name = "Card", description = "Card 관련 API")
public class CardController {
    private final CardService cardService;

    @Operation(summary = "실물 카드 발급", description = "현재 로그인한 사용자에게 실물 카드를 발급합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "실물 카드 발급 성공"),
            @ApiResponse(responseCode = "400", description = "실물 카드 발급 실패",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorCode.class),
                            examples = @ExampleObject(value = "{\n  \"code\": \"C002\"," +
                                                               "\n  \"message\": \"실물 카드 발급에 실패했습니다.\"\n}"))
            )
    })
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/physical")
    public void generatePhysicalCard(@AuthenticationPrincipal CustomUserDetails customUserDetails) {
        cardService.generatePhysicalCard(customUserDetails.getId());
    }

    @Operation(summary = "카드 상태 변경", description = "현재 로그인한 사용자의 카드 상태를 변경합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "카드 상태 변경 성공"),
            @ApiResponse(responseCode = "400", description = "카드 상태 변경 실패",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorCode.class),
                            examples = @ExampleObject(value = "{\n  \"code\": \"C004\"," +
                                                               "\n  \"message\": \"잘못된 카드 번호입니다.\"\n}"))
            )
    })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PutMapping("/status")
    public void changeCardStatus(@AuthenticationPrincipal CustomUserDetails customUserDetails,
                                 @RequestBody @Valid ChangeCardStatusRequest request) {
        cardService.changeCardStatus(customUserDetails.getId(), request);
    }

    @Operation(summary = "보유 카드 목록 조회", description = "현재 로그인한 사용자의 보유 카드 목록을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "카드 목록 조회 성공"),
            @ApiResponse(responseCode = "400", description = "보유 카드 없음",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorCode.class),
                            examples = @ExampleObject(value = "{\n  \"code\": \"C003\"," +
                                                               "\n  \"message\": \"찾는 카드가 존재하지 않습니다.\"\n}"))
            )
    })
    @ResponseStatus(HttpStatus.OK)
    @GetMapping
    public List<BasicCardInfoResponse> getBasicCardInfo(@AuthenticationPrincipal CustomUserDetails customUserDetails) {
        return cardService.getBasicCardInfo(customUserDetails.getId());
    }

    @Operation(summary = "카드 상세 정보 조회", description = "특정 카드의 상세 정보를 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "카드 상세 정보 조회 성공"),
            @ApiResponse(responseCode = "400", description = "카드 상세 정보 조회 실패",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorCode.class),
                            examples = @ExampleObject(value = "{\n  \"code\": \"C003\"," +
                                                               "\n  \"message\": \"찾는 카드가 존재하지 않습니다.\"\n}"))
            )
    })
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/{cardId}")
    public DetailedCardInfoResponse getDetailedCardInfo(@PathVariable Long cardId) {
        return cardService.getDetailedCardInfo(cardId);
    }
}
