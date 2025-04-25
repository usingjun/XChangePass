package bumblebee.xchangepass.domain.card.service;

import bumblebee.xchangepass.domain.card.dto.request.ChangeCardStatusRequest;
import bumblebee.xchangepass.domain.card.dto.response.BasicCardInfoResponse;
import bumblebee.xchangepass.domain.card.dto.response.DetailedCardInfoResponse;
import bumblebee.xchangepass.domain.card.entity.Card;
import bumblebee.xchangepass.domain.card.entity.CardType;
import bumblebee.xchangepass.domain.card.repository.CardRepository;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.user.repository.UserRepository;
import bumblebee.xchangepass.domain.wallet.wallet.entity.Wallet;
import bumblebee.xchangepass.global.error.ErrorCode;
import bumblebee.xchangepass.global.exception.CommonException;
import bumblebee.xchangepass.global.security.crypto.AESEncryption;
import bumblebee.xchangepass.global.security.crypto.RSAEncryption;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CardService {

    private final CardRepository cardRepository;
    private final UserRepository userRepository;
    private final CardCacheService cardCacheService;
    private final CardFactory cardFactory;
    private final RSAEncryption rsaEncryption;

    /**
     * ✅ 모바일 카드 발급
     */
    public void generateMobileCard(Wallet wallet) {
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
            String encryptionAesKey = rsaEncryption.encryptAESKeyWithKMS(aesKey);

            Card mobileCard = Card.builder()
                    .cardNumber(encryptionCardNumber)
                    .cvc(encryptionCvc)
                    .cardType(CardType.MOBILE)
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

    /**
     * ✅ 실물 카드 발급
     */
    @Transactional
    public void generatePhysicalCard(Long userId) {

        User existUser = userRepository.findByUserId(userId)
                .orElseThrow(ErrorCode.USER_NOT_FOUND::commonException);

        if(existUser.getWallet().getCards().stream().anyMatch(c -> c.getCardType().equals(CardType.PHYSICAL))) {
            throw ErrorCode.ALREADY_ISSUED_PHYSICAL_CARD.commonException();
        }

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
            String encryptionAesKey = rsaEncryption.encryptAESKeyWithKMS(aesKey);

            Card mobileCard = Card.builder()
                    .cardNumber(encryptionCardNumber)
                    .cvc(encryptionCvc)
                    .cardType(CardType.PHYSICAL)
                    .encryptedAesKey(encryptionAesKey)
                    .iv(iv)
                    .wallet(existUser.getWallet())
                    .build();

            cardRepository.save(mobileCard);

            existUser.getWallet().getCards().add(mobileCard);
        }catch (CommonException e) {
            throw e;
        }catch (Exception e) {
            throw ErrorCode.PHYSICAL_CARD_GENERATION_FAILED.commonException();
        }
    }

    /**
     * ✅ 카드 상태 변경
     */
    @Transactional
    public void changeCardStatus(Long userId,ChangeCardStatusRequest request) {
        User existUser = userRepository.findByUserId(userId)
                .orElseThrow(ErrorCode.USER_NOT_FOUND::commonException);

        Card card = existUser.getWallet().getCards().stream()
                .filter(c -> c.getCardType().equals(request.cardType()))
                .findFirst()
                .orElseThrow(ErrorCode.CARD_NOT_FOUND::commonException);

        card.changeStatus(request.status());

        DetailedCardInfoResponse cachedCard = cardCacheService.getCardInfo(card.getCardId());

        if (cachedCard != null) {
            DetailedCardInfoResponse updatedCardInfo = DetailedCardInfoResponse.builder()
                    .cardType(cachedCard.cardType())
                    .cardStatus(request.status())
                    .cardNumber(cachedCard.cardNumber())
                    .cvc(cachedCard.cvc())
                    .expirationDate(cachedCard.expirationDate())
                    .cardCreateDate(cachedCard.cardCreateDate())
                    .build();

            cardCacheService.saveCardInfo(card.getCardId(), updatedCardInfo);
        }
    }

    /**
     * ✅ 카드 관리 - 보유 카드 목록 조회
     */
    @Transactional(readOnly = true)
    public List<BasicCardInfoResponse> getBasicCardInfo(Long userId) {
        User existUser = userRepository.findByUserId(userId)
                .orElseThrow(ErrorCode.USER_NOT_FOUND::commonException);

        List<Card> cards = existUser.getWallet().getCards();

        return cards.stream()
                .map(card -> {
                    // 1️⃣ Redis에서 카드 정보 조회
                    DetailedCardInfoResponse cachedCard = cardCacheService.getCardInfo(card.getCardId());

                    // 2️⃣ Redis에 없으면 복호화 후 저장
                    if (cachedCard == null) {
                        cachedCard = cacheCardInfo(card);
                    }

                    // 3️⃣ `DetailedCardInfoResponse` → `BasicCardInfoResponse` 변환 (마스킹된 카드 번호)
                    return BasicCardInfoResponse.from(cachedCard);
                })
                .collect(Collectors.toList());
    }

    /**
     * ✅ 카드 정보 조회
     */
    public DetailedCardInfoResponse getDetailedCardInfo(Long cardId) {
        DetailedCardInfoResponse cachedCard = cardCacheService.getCardInfo(cardId);

        if (cachedCard == null) {
            Card card = cardRepository.findById(cardId)
                    .orElseThrow(ErrorCode.CARD_NOT_FOUND::commonException);

            cachedCard = cacheCardInfo(card);
        }

        return cachedCard;
    }

    /**
     * ✅ Redis에 카드 정보 저장
     */
    private DetailedCardInfoResponse cacheCardInfo(Card card) {
        SecretKey decryptedAESKey = rsaEncryption.decryptAESKeyWithKMS(card.getEncryptionData().getEncryptedAesKey());

        String decryptedCardNumber = AESEncryption.decryptData(card.getCardNumber(), decryptedAESKey, card.getEncryptionData().getIv());
        String decryptedCvc = AESEncryption.decryptData(card.getCvc(), decryptedAESKey, card.getEncryptionData().getIv());

        DetailedCardInfoResponse cardInfoDTO = DetailedCardInfoResponse.builder()
                .cardId(card.getCardId())
                .cardType(card.getCardType())
                .cardStatus(card.getCardStatus())
                .cardNumber(decryptedCardNumber)
                .cvc(decryptedCvc)
                .expirationDate(card.getExpirationDate())
                .cardCreateDate(card.getCardCreateDate())
                .build();

        cardCacheService.saveCardInfo(card.getCardId(), cardInfoDTO);

        return cardInfoDTO;
    }
}
