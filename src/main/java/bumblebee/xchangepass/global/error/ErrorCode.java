package bumblebee.xchangepass.global.error;

import bumblebee.xchangepass.global.exception.CommonException;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    /*User*/
    USER_NOT_FOUND(HttpStatus.BAD_REQUEST, "U001", "존재 하지 않는 회원입니다."),
    USER_NOT_MODIFY(HttpStatus.BAD_REQUEST, "U002", "회원 수정에 실패했습니다."),
    USER_NOT_REGISTER(HttpStatus.BAD_REQUEST, "U003", "회원가입에 실패했습니다."),
    USER_NOT_DELETE(HttpStatus.BAD_REQUEST, "U004", "회원 삭제 요청에 실패했습니다."),
    USER_DUPLICATE_EMAIL(HttpStatus.BAD_REQUEST, "U005", "중복된 이메일 입니다."),
    USER_DUPLICATE_NICK_NAME(HttpStatus.BAD_REQUEST, "U006", "중복된 닉네임 입니다."),
    USER_DUPLICATE_PHONE_NUMBER(HttpStatus.BAD_REQUEST, "U007", "중복된 전화번호 입니다."),

    //Balance
    BALANCE_NOT_FOUND(HttpStatus.BAD_REQUEST, "M005-1", "충전 금액이 부족합니다."),
    BALANCE_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "M005-2", "충전 금액이 부족합니다."),

    /*기타*/
    ENTITY_FIELD_ACCESS_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "G001", "엔티티 필드 접근 오류");

    @Schema(description = "에러 코드", example = "U003")
    private final String code;

    @Schema(description = "에러 메시지", example = "회원가입에 실패했습니다.")
    private final String message;

    @Schema(description = "HTTP 상태 코드", example = "200, 400")
    private final HttpStatus status;

    ErrorCode(HttpStatus status,String code, String message) {
        this.status = status;
        this.message = message;
        this.code = code;
    }

    public CommonException commonException() {
        return new CommonException(this);
    }

}
