package bumblebee.xchangepass.domain.card.dto.response;

import bumblebee.xchangepass.domain.card.entity.CardStatus;
import bumblebee.xchangepass.domain.card.entity.CardType;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.io.Serializable;
import java.time.LocalDateTime;

@Schema(description = "세부 카드 정보 응답 DTO")
@Builder
public record DetailedCardInfoResponse(
        @Schema(description = "카드 타입", example = "PHYSICAL")
        CardType cardType,

        @Schema(description = "카드 상태", example = "ACTIVE")
        CardStatus cardStatus,

        @Schema(description = "카드 번호", example = "1111-1111-1111-1234")
        String cardNumber,

        @Schema(description = "CVC", example = "123")
        String cvc,

        @Schema(description = "카드 만료일", example = "01 / 30")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "MM/yy")
        LocalDateTime expirationDate,

        @Schema(description = "카드 생성일", example = "2023-05-01")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDateTime cardCreateDate
) implements Serializable {
}
