package bumblebee.xchangepass.global.common;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EncryptionData {

    @Column(name = "encrypted_aes_key", nullable = false, length = 512)
    private String encryptedAesKey;

    @Column(name = "iv", nullable = false)
    private byte[] iv;

    public EncryptionData(String encryptedAesKey, byte[] iv) {
        this.encryptedAesKey = encryptedAesKey;
        this.iv = iv;
    }
}