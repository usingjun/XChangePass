package bumblebee.xchangepass.domain.user.dto.request;

import bumblebee.xchangepass.domain.user.entity.Sex;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.user.entity.value.UserEmail;
import bumblebee.xchangepass.domain.user.entity.value.UserName;
import bumblebee.xchangepass.domain.user.entity.value.UserPassword;
import bumblebee.xchangepass.domain.user.entity.value.UserPhoneNumber;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Schema(description = "사용자를 등록하기 위한 요청 객체")
@Builder
public record UserRegisterRequest(
        @Schema(description = "사용자 이메일", example = "example@mail.com")
        @Pattern(regexp = UserEmail.REGEX, message = UserEmail.ERR_MSG)
        @NotNull(message = "사용자 이메일은 필수 입력 값입니다.")
        String userEmail,

        @Schema(description = "사용자 비밀번호", example = "1234")
        @Pattern(regexp = UserPassword.REGEX, message = UserPassword.ERR_MSG)
        @NotNull(message = "사용자 비밀번호는 필수 입력 값입니다.")
        String userPwd,

        @Schema(description = "사용자 실명", example = "홍길동")
        @Pattern(regexp = UserName.REGEX, message = UserName.ERR_MSG)
        @NotNull(message = "사용자 실명은 필수 입력 값입니다.")
        String userName,

        @Schema(description = "사용자 전화번호", example = "010-0000-000")
        @Pattern(regexp = UserPhoneNumber.REGEX, message = UserPhoneNumber.ERR_MSG)
        @NotNull(message = "사용자 전화번호는 필수 입력 값입니다.")
        String userPhoneNumber,

        @Schema(description = "사용자의 성별", example = "MALE")
        @NotNull(message = "성별 입력해주세요")
        Sex userSex
) {

    public User toEntity(final PasswordEncoder passwordEncoder, String uniqueNickname) {
        return   User.builder()
                     .userEmail(userEmail)
                     .userPwd(userPwd)
                     .userName(userName)
                     .userNickname(uniqueNickname)
                     .userPhoneNumber(userPhoneNumber)
                     .userSex(userSex)
                     .passwordEncoder(passwordEncoder)
                     .build();
    }

}
