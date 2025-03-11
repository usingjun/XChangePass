package bumblebee.xchangepass.domain.card.service;

import bumblebee.xchangepass.domain.card.entity.Card;
import bumblebee.xchangepass.domain.card.entity.CardType;
import bumblebee.xchangepass.domain.card.repository.CardRepository;
import bumblebee.xchangepass.domain.wallet.entity.Wallet;
import bumblebee.xchangepass.global.error.ErrorCode;
import bumblebee.xchangepass.global.exception.CommonException;
import bumblebee.xchangepass.global.security.crypto.AESEncryption;
import bumblebee.xchangepass.global.security.crypto.RSAEncryption;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;

@Service
@RequiredArgsConstructor
public class CardService {

    private final CardRepository cardRepository;
    private final CardFactory cardFactory;

    /**
     * ✅ 카드 발급
     */
    public void generateCard(Wallet wallet, CardType cardType) {
        String cardNumber = cardFactory.generateCardNumber();
        String cvc = cardFactory.generateCvc();

        try {
            //AES 키 생성 및 IV 생성
            SecretKey aesKey = AESEncryption.generateAESKey();
            byte[] iv = AESEncryption.generateIV();

            //AES 암호화
            String encryptionCardNumber = AESEncryption.encryptData(cardNumber, aesKey, iv);
            String encryptionCvc = AESEncryption.encryptData(cvc, aesKey, iv);

            //AES 키 RSA 암호화
            String encryptionAesKey = RSAEncryption.encryptAESKeyWithRSA(aesKey);

            Card mobileCard = Card.builder()
                    .cardNumber(encryptionCardNumber)
                    .cvc(encryptionCvc)
                    .cardType(cardType)
                    .encryptedAesKey(encryptionAesKey)
                    .iv(iv)
                    .wallet(wallet)
                    .build();

            cardRepository.save(mobileCard);
        }catch (CommonException e) {
            throw e;
        }catch (Exception e) {
            throw ErrorCode.MOBILE_CARD_GENERATION_FAILED.commonException();
        }
    }

}
