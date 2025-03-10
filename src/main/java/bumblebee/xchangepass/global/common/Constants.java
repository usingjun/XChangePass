package bumblebee.xchangepass.global.common;

public class Constants {
    //Redis 닉네임 INCR 키
    public static final String NICKNAME_KEY = "nickname:counter";

    //Redis Port 번호
    public static final int REDIS_PORT = 6379;

    //RSA 공개키 Base64
    public static final String RSA_PUBLIC_KEY_BASE64 = """
            MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqSO708wOZkGq1ULteBcm
            Rw8rrkI+ix53EOE/7VFstWlJkv7XRRtVhXxuW11BGDiz0WCGxM2zo4AEK1lts0kX
            tX1HFTrzV3UjjAZad1sAeRdjUg3vq5Ky1GpFkNx3MxpZO2bizj97gUKfsJvZsz07
            LH5ZsEnkZKU1vViYiYmDtIIlJVH71gRotAHCUOVcXmcfWSmpOHwv2ZPgZN/MxI79
            2NSSjLNwoBw2yMa3i9vbd0iaHtUod0TseLzgHqZMDLfnkxHGmepqU/B3ZQuajR2B
            FEETN1/71J4ZStt2CjC1/Cgkw1Z+xVCZ/M9/GDJRgwQ5sRgYkrQIDlA4F5pxmBVL
            0QIDAQAB
            """;
}