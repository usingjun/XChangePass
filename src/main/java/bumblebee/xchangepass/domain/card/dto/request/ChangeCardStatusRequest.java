package bumblebee.xchangepass.domain.card.dto.request;

import bumblebee.xchangepass.domain.card.entity.CardStatus;
import bumblebee.xchangepass.domain.card.entity.CardType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Schema(description = "카드 상태 변경 요청 객체")
@Builder
public record ChangeCardStatusRequest(
        @Schema(description = "변경할 카드 타입", example = "PHYSICAL")
        @NotNull(message = "카드 타입은 필수 입력 값입니다.")
        CardType cardType,

        @Schema(description = "변경할 카드 상태", example = "INACTIVE")
        @NotNull(message = "카드 상태는 필수 입력 값입니다.")
        CardStatus status
) {
}
