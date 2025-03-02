package bumblebee.xchangepass.domain.user.entity;

import bumblebee.xchangepass.global.error.ErrorCode;

import java.util.Arrays;

public enum Sex {
    MALE,
    FEMALE;

    public static Sex fromString(String value) {
        return Arrays.stream(Sex.values())
                .filter(sex -> sex.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(ErrorCode.INVALID_GENDER::commonException);
    }
}
