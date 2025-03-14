package bumblebee.xchangepass.global.security.crypto;

import bumblebee.xchangepass.global.error.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DecryptResponse;
import software.amazon.awssdk.services.kms.model.EncryptRequest;
import software.amazon.awssdk.services.kms.model.EncryptResponse;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Component
public class RSAEncryption {

    private final KmsClient kmsClient;
    private final String kmsKeyId;

    @Autowired
    public RSAEncryption(KmsClient kmsClient, @Value("${aws.kms.key-id}") String kmsKeyId) {
        this.kmsClient = kmsClient;
        this.kmsKeyId = kmsKeyId;
    }

    /**
     * ✅ AWS KMS 공개 키를 사용한 AES 키 암호화
     */
    public String encryptAESKeyWithKMS(SecretKey aesKey) {
        try {
            EncryptRequest encryptRequest = EncryptRequest.builder()
                    .keyId(kmsKeyId)
                    .plaintext(SdkBytes.fromByteArray(aesKey.getEncoded()))
                    .encryptionAlgorithm("RSAES_OAEP_SHA_256")
                    .build();

            EncryptResponse encryptResponse = kmsClient.encrypt(encryptRequest);
            return Base64.getEncoder().encodeToString(encryptResponse.ciphertextBlob().asByteArray());
        } catch (Exception e) {
            throw ErrorCode.AES_ENCRYPTION_FAILED.commonException();
        }
    }


    /**
     * ✅ AWS KMS를 이용한 AES 키 복호화
     */
    public SecretKey decryptAESKeyWithKMS(String encryptedAESKeyBase64) {
        try {
            byte[] encryptedKeyBytes = Base64.getDecoder().decode(encryptedAESKeyBase64);
            SdkBytes encryptedKey = SdkBytes.fromByteArray(encryptedKeyBytes);

            DecryptRequest decryptRequest = DecryptRequest.builder()
                    .keyId(kmsKeyId)
                    .ciphertextBlob(encryptedKey)
                    .encryptionAlgorithm("RSAES_OAEP_SHA_256")
                    .build();

            DecryptResponse decryptResponse = kmsClient.decrypt(decryptRequest);
            byte[] decryptedKeyBytes = decryptResponse.plaintext().asByteArray();

            return new SecretKeySpec(decryptedKeyBytes, "AES");
        } catch (Exception e) {
            throw ErrorCode.AES_DECRYPTION_FAILED.commonException();
        }
    }
}
