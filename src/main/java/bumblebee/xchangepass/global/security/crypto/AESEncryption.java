package bumblebee.xchangepass.global.security.crypto;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

public class AESEncryption {

    // AES-256 키 생성
    public static SecretKey generateAESKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        return keyGen.generateKey();
    }

    // IV 생성
    public static byte[] generateIV() {
        byte[] iv = new byte[16]; // AES 블록 크기는 16바이트
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    // AES CBC 모드를 사용한 데이터 암호화
    public static String encryptData(String data, SecretKey secretAESKey, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec secretKeySpec = new SecretKeySpec(secretAESKey.getEncoded(), "AES");
        IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec);

        byte[] encryptedData = cipher.doFinal(data.getBytes());
        return Base64.getEncoder().encodeToString(encryptedData);
    }

    // AES CBC 모드를 사용한 데이터 복호화
    public static String decryptData(String encryptedData, SecretKey secretKey, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec);

        byte[] decodedEncryptedData = Base64.getDecoder().decode(encryptedData);
        byte[] decryptedData = cipher.doFinal(decodedEncryptedData);
        return new String(decryptedData).trim();
    }

}
