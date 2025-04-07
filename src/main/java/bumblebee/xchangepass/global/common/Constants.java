package bumblebee.xchangepass.global.common;

public class Constants {
    //Redis 닉네임 INCR 키
    public static final String NICKNAME_KEY = "nickname:counter";

    //Redis Port 번호
    public static final int REDIS_PORT = 6379;

    //RabbitMQ 큐 이름
    public static final String WALLET_TRANSACTION = "wallet-transaction-queue";
    public static final String DLQ_NAME = "wallet-transaction-dlx-queue";
    public static final String DLX_NAME = "wallet-transaction-dlx";
    public static final String DLQ_ROUTING_KEY = "wallet-transaction-dlx-key";
    public static final String RETRY_QUEUE = "wallet-transaction-retry-queue";

    // 암호화 알고리즘 상수
    public static final String AES_CBC_PADDING = "AES/CBC/PKCS5Padding";
    public static final String RSAES_OAEP_SHA_256 = "RSAES_OAEP_SHA_256";

    // Token 유효 기간
    public static final Long REFRESH_TOKEN_TTL = 24 * 60 * 60L; // 24시간 (초 단위)
    public static final Long JWT_TOKEN_VALID = 1000 * 60 * 30L; // jwt AccessToken 만료 시간 1시간
}