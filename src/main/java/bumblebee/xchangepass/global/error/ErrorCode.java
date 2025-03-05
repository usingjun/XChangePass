package bumblebee.xchangepass.global.error;

import bumblebee.xchangepass.global.exception.CommonException;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    /*Member*/
    USER_NOT_FOUND(HttpStatus.BAD_REQUEST, "U001", "존재 하지 않는 회원입니다."),
    USER_UPDATE_EXCEPTION(HttpStatus.BAD_REQUEST, "U002", "잘못된 회원 수정 입니다."),
    USER_DUPLICATE_EMAIL(HttpStatus.BAD_REQUEST, "U003", "중복된 이메일 입니다."),
    USER_DUPLICATE_NICK_NAME(HttpStatus.BAD_REQUEST, "U004", "중복된 닉네임 입니다."),
    USER_DUPLICATE_PHONE_NUMBER(HttpStatus.BAD_REQUEST, "U005", "중복된 전화번호 입니다."),
    USER_NOT_MODIFY(HttpStatus.BAD_REQUEST,"U006","회원 수정 실패"),

    //Balance
    BALANCE_NOT_FOUND(HttpStatus.BAD_REQUEST, "M005-1", "존재하지 않는 지갑입니다."),
    BALANCE_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "M005-2", "충전 금액이 부족합니다."),

    //Exchange_rate
    EXCHANGE_RATE_NOT_FOUND(HttpStatus.NOT_FOUND, "E001", "존재 하지 않는 환율입니다."),
    EXCHANGE_RATE_FOR_COUNTRY(HttpStatus.BAD_REQUEST, "E003", "이 나라에 대한 환율 정보가 없습니다."),
    EXCHANGE_SAVE_FAIL(HttpStatus.BAD_REQUEST, "E002", "환율 정보 저장 실패"),

    //TRANSACTION
    TRANSACTION_ALREADY_COMPLETED(HttpStatus.BAD_REQUEST, "T001", "이미 완료된 거래입니다."),
    TRANSACTION_AMOUNT_NOTFOUND(HttpStatus.BAD_REQUEST,"T003", "환전 금액은 필수 입니다."),
    TRANSACTION_HISTORY_NOT_FOUND(HttpStatus.NOT_FOUND, "T002", "존재 하지 않는 환전 내역입니다."),
    TRANSACTION_USERID_NOT_FOUND(HttpStatus.NOT_FOUND, "T004", "회원 아이디 필수"),
    /*기타*/
    ENTITY_FIELD_ACCESS_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "G001", "엔티티 필드 접근 오류");

    private final String code;
    private final String message;
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
