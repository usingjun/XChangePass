package bumblebee.xchangepass.domain.card.dto.response;

import bumblebee.xchangepass.domain.card.entity.CardStatus;
import bumblebee.xchangepass.domain.card.entity.CardType;
import bumblebee.xchangepass.global.error.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Schema(description = "기본 카드 정보 응답 DTO")
@Builder
public record BasicCardInfoResponse(
        @Schema(description = "카드 ID", example = "1")
        Long cardId,

        @Schema(description = "카드 타입", example = "PHYSICAL")
        CardType cardType,

        @Schema(description = "카드 상태", example = "ACTIVE")
        CardStatus cardStatus,

        @Schema(description = "마스킹된 카드 번호", example = "1111-****-****-1234")
        String maskedCardNumber
) {

    /**
     * 카드 번호 마스킹 처리 (예: "1111-****-****-1234")
     */
    public static String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.replaceAll("-", "").length() < 16) {
            throw ErrorCode.INVALID_CARD_NUMBER.commonException();
        }
        return cardNumber.substring(0, 4) + "-****-****-" + cardNumber.substring(cardNumber.length() - 4);
    }

    /**
     * 'DetailedCardInfoResponse' 에서 변환하는 팩토리 메서드
     */
    public static BasicCardInfoResponse from(DetailedCardInfoResponse detailedInfo) {
        return BasicCardInfoResponse.builder()
                .cardId(detailedInfo.cardId())
                .cardType(detailedInfo.cardType())
                .cardStatus(detailedInfo.cardStatus())
                .maskedCardNumber(maskCardNumber(detailedInfo.cardNumber()))
                .build();
    }
}

