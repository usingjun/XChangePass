package bumblebee.xchangepass.domain.user.dto.response;

import bumblebee.xchangepass.domain.user.entity.Sex;
import bumblebee.xchangepass.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "사용자 정보를 전달하기 위한 응답 객체")
public record UserResponse(
        @Schema(description = "사용자 이메일", example = "example@mail.com")
        String userEmail,

        @Schema(description = "사용자 실명", example = "홍길동")
        String userName,

        @Schema(description = "사용자 닉네임", example = "홍길동123")
        String userNickName,

        @Schema(description = "사용자 전화번호", example = "010-0000-0000")
        String userPhoneNumber,

        @Schema(description = "사용자 나이", example = "25")
        Integer userAge,

        @Schema(description = "사용자의 성별", example = "MALE")
        Sex userSex,

        @Schema(description = "사용자 가입 날짜", example = "2024-02-20T12:34:56")
        LocalDateTime userJoinDate
){

    public UserResponse(User user) {
        this(
                user.getUserEmail().getValue(),
                user.getUserName().getValue(),
                user.getUserNickname().getValue(),
                user.getUserPhoneNumber().getValue(),
                user.getUserAge(),
                user.getUserSex(),
                LocalDateTime.now()
        );
    }

}
