package bumblebee.xchangepass.domain.user.dto.request;

import bumblebee.xchangepass.domain.user.entity.Sex;
import bumblebee.xchangepass.domain.user.entity.value.UserNickname;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;

@Schema(description = "사용자 정보를 업데이트하기 위한 요청 객체")
@Builder
public record UserUpdateRequest(
        @Schema(description = "사용자의 닉네임", example = "홍길동12")
        @Pattern(regexp = UserNickname.REGEX, message = UserNickname.ERR_MSG)
        @NotNull(message = "사용자 닉네임은 필수 입력 값입니다.")
        String userNickname,

        @Schema(description = "사용자의 나이", example = "25")
        @NotNull(message = "나이는 필수 입력 값입니다.")
        Integer userAge,

        @Schema(description = "사용자의 성별", example = "MALE")
        @NotNull(message = "성별은 필수 입력 값입니다.")
        Sex userSex
) {

}
