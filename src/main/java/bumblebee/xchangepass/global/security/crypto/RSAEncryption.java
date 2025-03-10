package bumblebee.xchangepass.global.security.crypto;

import bumblebee.xchangepass.global.common.Constants;
import bumblebee.xchangepass.global.error.ErrorCode;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class RSAEncryption {

    private static final PublicKey PUBLIC_KEY;

    static {
        try {
            byte[] decodedKey = Base64.getDecoder().decode(Constants.RSA_PUBLIC_KEY_BASE64);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decodedKey);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PUBLIC_KEY = keyFactory.generatePublic(keySpec);
        } catch (Exception e) {
            throw ErrorCode.RSA_KEY_ROAD_FAILED.commonException();
        }
    }


    // RSA 퍼블릭 키를 사용한 AES 키 암호화
    public static String encryptAESKeyWithRSA(SecretKey aesKey) {
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, PUBLIC_KEY);

            byte[] encryptedKey = cipher.doFinal(aesKey.getEncoded());
            return Base64.getEncoder().encodeToString(encryptedKey);
        }catch (Exception e) {
            throw ErrorCode.AES_ENCRYPTION_FAILED.commonException();
        }
    }
}
