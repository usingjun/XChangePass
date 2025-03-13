package bumblebee.xchangepass.global.security.crypto;

import bumblebee.xchangepass.global.common.Constants;
import bumblebee.xchangepass.global.error.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DecryptResponse;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Component
public class RSAEncryption {

    private final KmsClient kmsClient;
    private static final PublicKey PUBLIC_KEY;

    @Autowired
    public RSAEncryption(KmsClient kmsClient) {
        this.kmsClient = kmsClient;
    }

    static {
        try {
            String cleanBase64Key = Constants.RSA_PUBLIC_KEY_BASE64
                    .replaceAll("\\s+", "");  // 모든 공백 및 개행 문자 제거

            byte[] decodedKey = Base64.getDecoder().decode(cleanBase64Key);
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


    // AWS KMS를 이용한 AES 키 복호화
    public  SecretKey decryptAESKeyWithKMS(String encryptedAESKeyBase64) {
        try {
            byte[] encryptedKeyBytes = Base64.getDecoder().decode(encryptedAESKeyBase64);
            SdkBytes encryptedKey = SdkBytes.fromByteArray(encryptedKeyBytes);

            DecryptRequest decryptRequest = DecryptRequest.builder()
                    .ciphertextBlob(encryptedKey)
                    .build();

            DecryptResponse decryptResponse = kmsClient.decrypt(decryptRequest);
            byte[] decryptedKeyBytes = decryptResponse.plaintext().asByteArray();

            return new SecretKeySpec(decryptedKeyBytes, "AES");

        } catch (Exception e) {
            throw ErrorCode.AES_DECRYPTION_FAILED.commonException();
        }
    }
}
