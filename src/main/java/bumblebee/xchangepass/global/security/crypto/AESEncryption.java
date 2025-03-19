package bumblebee.xchangepass.global.security.crypto;

import bumblebee.xchangepass.global.error.ErrorCode;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

import static bumblebee.xchangepass.global.common.Constants.AES_CBC_PADDING;

public class AESEncryption {

    // AES-256 키 생성
    public static SecretKey generateAESKey(){
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256);
            return keyGen.generateKey();
        }catch (Exception e) {
            throw ErrorCode.AES_KEY_GENERATION_FAILED.commonException();
        }
    }

    // IV 생성
    public static byte[] generateIV() {
        try {
            byte[] iv = new byte[16]; // AES 블록 크기는 16바이트
            new SecureRandom().nextBytes(iv);
            return iv;
        }catch (Exception e) {
            throw ErrorCode.IV_GENERATION_FAILED.commonException();
        }
    }

    // AES CBC 모드를 사용한 데이터 암호화
    public static String encryptData(String data, SecretKey secretAESKey, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance(AES_CBC_PADDING);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretAESKey.getEncoded(), "AES");
            IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec);

            byte[] encryptedData = cipher.doFinal(data.getBytes());
            return Base64.getEncoder().encodeToString(encryptedData);
        }catch (Exception e) {
            throw ErrorCode.DATE_ENCRYPTION_FAILED.commonException();
        }
    }

    // AES CBC 모드를 사용한 데이터 복호화
    public static String decryptData(String encryptedData, SecretKey secretKey, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance(AES_CBC_PADDING);
            IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec);

            byte[] decodedEncryptedData = Base64.getDecoder().decode(encryptedData);
            byte[] decryptedData = cipher.doFinal(decodedEncryptedData);
            return new String(decryptedData).trim();
        }catch (Exception e) {
            throw ErrorCode.DATE_DECRYPTION_FAILED.commonException();
        }
    }

}
