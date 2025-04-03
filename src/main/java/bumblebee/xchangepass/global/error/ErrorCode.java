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
    USER_NOT_DELETE(HttpStatus.BAD_REQUEST, "U004", "회원 삭제에 실패했습니다."),
    USER_DUPLICATE_EMAIL(HttpStatus.BAD_REQUEST, "U005", "중복된 이메일 입니다."),
    USER_DUPLICATE_NICK_NAME(HttpStatus.BAD_REQUEST, "U006", "중복된 닉네임 입니다."),
    USER_DUPLICATE_PHONE_NUMBER(HttpStatus.BAD_REQUEST, "U007", "중복된 전화번호 입니다."),
    INVALID_GENDER(HttpStatus.BAD_REQUEST, "U008", "잘못된 성별 값입니다."),
    INVALID_NICKNAME_PREFIX(HttpStatus.BAD_REQUEST, "U010", "닉네임은 'User_'로 시작할 수 없습니다."),

    /*Wallet*/
    WALLET_NOT_FOUND(HttpStatus.BAD_REQUEST, "W001", "지갑을 찾을 수 없습니다."),
    WALLET_ALREADY_EXIST(HttpStatus.BAD_REQUEST,"W002","이미 지갑이 존재합니다."),

    /*Balance*/
    BALANCE_NOT_FOUND(HttpStatus.BAD_REQUEST, "B001", "해당 화폐 잔액이 존재하지 않습니다."),
    BALANCE_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "B002", "충전 금액이 부족합니다."),

    /*Exchange_rate*/
    EXCHANGE_RATE_NOT_FOUND(HttpStatus.NOT_FOUND, "E001", "존재 하지 않는 환율입니다."),
    EXCHANGE_SAVE_FAIL(HttpStatus.BAD_REQUEST, "E002", "환율 정보 저장 실패"),
    EXCHANGE_RATE_FOR_COUNTRY(HttpStatus.BAD_REQUEST, "E003", "이 나라에 대한 환율 정보가 없습니다."),
   EXCHANGE_RATE_EXCEED(HttpStatus.TOO_MANY_REQUESTS, "E004", "환율 요청 초과"),
    EXCHANGE_DATA_ACCESS_EXCEPTION(HttpStatus.INTERNAL_SERVER_ERROR, "E005", "데이터베이스 접근 중 오류 발생"),
    EXCHANGE_SQL_EXECUTION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "E006", "SQL 실행 중 오류 발생"),
    EXCHANGE_TABLE_DROP_FAIL(HttpStatus.INTERNAL_SERVER_ERROR, "E007", "테이블 DML 중 오류 발생"),
    EXCHANGE_TABLE_RENAME_FAIL(HttpStatus.INTERNAL_SERVER_ERROR, "E008", "테이블 이름 변경 실패"),
    EXCHANGE_TABLE_CREATION_FAIL(HttpStatus.INTERNAL_SERVER_ERROR, "E009", "임시 테이블 생성 실패"),

    /*TRANSACTION*/
    TRANSACTION_ALREADY_COMPLETED(HttpStatus.BAD_REQUEST, "T001", "이미 완료된 거래입니다."),
    TRANSACTION_AMOUNT_NOTFOUND(HttpStatus.BAD_REQUEST,"T003", "환전 금액은 필수 입니다."),
    TRANSACTION_HISTORY_NOT_FOUND(HttpStatus.NOT_FOUND, "T002", "존재 하지 않는 환전 내역입니다."),
    TRANSACTION_USERID_NOT_FOUND(HttpStatus.NOT_FOUND, "T004", "회원 아이디 필수"),
    UNAUTHORIZED_TRANSACTION_ACCESS(HttpStatus.UNAUTHORIZED, "T005", "당신의 환전 ID가 아닙니다."),

    /*Card*/
    MOBILE_CARD_GENERATION_FAILED(HttpStatus.BAD_REQUEST,"C001","모바일 카드 발급에 실패했습니다."),
    PHYSICAL_CARD_GENERATION_FAILED(HttpStatus.BAD_REQUEST,"C002","실물 카드 발급에 실패했습니다."),
    CARD_NOT_FOUND(HttpStatus.BAD_REQUEST,"C003","찾는 카드가 존재하지 않습니다."),
    INVALID_CARD_NUMBER(HttpStatus.BAD_REQUEST, "C004", "잘못된 카드 번호입니다."),


    /*Encryption*/
    AES_KEY_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "ENC001", "AES 키 생성에 실패했습니다."),
    RSA_KEY_ROAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "ENC002", "RSA 키 로드에 실패했습니다."),
    IV_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "ENC003", "IV 생성에 실패했습니다."),
    DATE_ENCRYPTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "ENC004", "데이터 암호화에 실패했습니다."),
    DATE_DECRYPTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "ENC005", "데이터 복호화에 실패했습니다."),
    AES_ENCRYPTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "ENC006", "AES 키 암호화에 실패했습니다."),
    AES_DECRYPTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "ENC007", "AES 키 복호화에 실패했습니다."),
    INVALID_AES_KEY(HttpStatus.BAD_REQUEST, "ENC008", "잘못된 AES 키입니다."),
    INVALID_IV(HttpStatus.BAD_REQUEST, "ENC009", "잘못된 IV 값입니다."),



    //Security
    USER_FORBIDDEN(HttpStatus.FORBIDDEN, "S0001", "권한이 없습니다."),
    LOGIN_NOT_CORRECT(HttpStatus.UNAUTHORIZED, "S002", "아이디 혹은 비밀번호가 일치하지 않습니다."),
    LOGIN_INVALID_INPUT(HttpStatus.BAD_REQUEST, "S003", "아이디 혹은 비밀번호를 입력하세요."),
    REFRESH_TOKEN_NOT_EXIST(HttpStatus.UNAUTHORIZED, "S004", "Refresh Token이 존재하지 않습니다."),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "S005", "Refresh Token이 만료되었거나 정상적인 Token이 아닙니다."),
    ACCESS_DENIED(HttpStatus.UNAUTHORIZED, "E0002", "인증되지 않은 사용자입니다."),

    /*기타*/
    ENTITY_FIELD_ACCESS_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "G001", "엔티티 필드 접근 오류"),
    REDIS_CONNECTION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "R001", "Redis 연결 실패");


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
