package bumblebee.xchangepass.global.util;

import bumblebee.xchangepass.global.error.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Map;

public class DuplicateKeyExceptionHandler {

    private static final Map<String, ErrorCode> CONSTRAINT_ERROR_MAP = Map.of(
            /* USER */
            "unique_user_email", ErrorCode.USER_DUPLICATE_EMAIL,
            "unique_user_nickname", ErrorCode.USER_DUPLICATE_NICK_NAME,
            "unique_user_phonenumber", ErrorCode.USER_DUPLICATE_PHONE_NUMBER
    );

    public static void handle(DataIntegrityViolationException e) {
        String errorMessage = e.getMostSpecificCause().getMessage();

        for (Map.Entry<String, ErrorCode> entry : CONSTRAINT_ERROR_MAP.entrySet()) {
            if (errorMessage.contains(entry.getKey())) {
                throw entry.getValue().commonException();
            }
        }

        throw new RuntimeException("Unknown database error occurred: " + errorMessage);
    }
}
