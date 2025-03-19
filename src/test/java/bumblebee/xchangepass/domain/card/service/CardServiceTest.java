package bumblebee.xchangepass.domain.card.service;


import bumblebee.xchangepass.config.RedisTestBase;
import bumblebee.xchangepass.config.TestUserInitializer;
import bumblebee.xchangepass.domain.card.dto.response.DetailedCardInfoResponse;
import bumblebee.xchangepass.domain.card.entity.Card;
import bumblebee.xchangepass.domain.card.entity.CardType;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.user.repository.UserRepository;
import bumblebee.xchangepass.global.error.ErrorCode;
import bumblebee.xchangepass.global.security.crypto.AESEncryption;
import bumblebee.xchangepass.global.security.crypto.RSAEncryption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestUserInitializer.class)
public class CardServiceTest extends RedisTestBase {

    @Autowired
    private CardService cardService;

    @Autowired
    private CardCacheService cardCacheService;

    @Autowired
    private RSAEncryption rsaEncryption;

    @Autowired
    private UserRepository userRepository;

    @Test
    @Transactional
    @DisplayName("실물 카드 발급 여부")
    void verifyPhysicalCardIssuance(){
        Long userId = 1L;

        cardService.generatePhysicalCard(userId);

        User user = userRepository.findByUserId(userId)
                .orElseThrow(ErrorCode.USER_NOT_FOUND::commonException);

        boolean hasPhysicalCard = user.getWallet().getCards().stream()
                .anyMatch(card -> card.getCardType().equals(CardType.PHYSICAL));

        assertTrue(hasPhysicalCard, "실물 카드가 정상적으로 발급되지 않았습니다.");
    }

    @Test
    @Transactional
    @DisplayName("카드 정보 조회 - 키 복호화 및 Redis 저장 상태 확인")
    void verifyKeyDecryptionAndRedisStorage() {
        Long userId = 2L;

        cardService.generatePhysicalCard(userId);

        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("테스트 유저가 존재하지 않습니다."));

        Card card = user.getWallet().getCards().stream()
                .findFirst()
                .orElseThrow(ErrorCode.CARD_NOT_FOUND::commonException);

        Long cardId = card.getCardId();

        DetailedCardInfoResponse cachedCardBefore = cardCacheService.getCardInfo(cardId);
        assertNull(cachedCardBefore, "Redis에 카드 정보가 미리 저장되지 않아야 합니다.");

        DetailedCardInfoResponse detailedCardInfo = cardService.getDetailedCardInfo(cardId);
        assertNotNull(detailedCardInfo, "복호화된 카드 정보를 가져올 수 있어야 합니다.");

        SecretKey decryptedAESKey = rsaEncryption.decryptAESKeyWithKMS(card.getEncryptionData().getEncryptedAesKey());

        String decryptedCardNumber = AESEncryption.decryptData(
                card.getCardNumber(), decryptedAESKey, card.getEncryptionData().getIv());

        String decryptedCvc = AESEncryption.decryptData(
                card.getCvc(), decryptedAESKey, card.getEncryptionData().getIv());

        assertEquals(decryptedCardNumber, detailedCardInfo.cardNumber(), "복호화된 카드 번호가 일치해야 합니다.");
        assertEquals(decryptedCvc, detailedCardInfo.cvc(), "복호화된 CVC가 일치해야 합니다.");

        DetailedCardInfoResponse cachedCardAfter = cardCacheService.getCardInfo(cardId);
        assertNotNull(cachedCardAfter, "Redis에 카드 정보가 저장되어야 합니다.");
        assertEquals(detailedCardInfo, cachedCardAfter, "Redis에 저장된 카드 정보가 복호화된 정보와 일치해야 합니다.");
    }

}
