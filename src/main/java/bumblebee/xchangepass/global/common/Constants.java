package bumblebee.xchangepass.global.common;

public class Constants {
    //Redis 닉네임 INCR 키
    public static final String NICKNAME_KEY = "nickname:counter";

    //Redis Port 번호
    public static final int REDIS_PORT = 6379;

    // 암호화 알고리즘 상수
    public static final String AES_CBC_PADDING = "AES/CBC/PKCS5Padding";
    public static final String RSAES_OAEP_SHA_256 = "RSAES_OAEP_SHA_256";
}