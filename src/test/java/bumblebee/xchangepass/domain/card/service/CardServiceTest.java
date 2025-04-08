package bumblebee.xchangepass.domain.card.service;


import bumblebee.xchangepass.config.RedisTestBase;
import bumblebee.xchangepass.config.TestUserInitializer;
import bumblebee.xchangepass.domain.card.dto.request.ChangeCardStatusRequest;
import bumblebee.xchangepass.domain.card.dto.response.BasicCardInfoResponse;
import bumblebee.xchangepass.domain.card.dto.response.DetailedCardInfoResponse;
import bumblebee.xchangepass.domain.card.entity.Card;
import bumblebee.xchangepass.domain.card.entity.CardStatus;
import bumblebee.xchangepass.domain.card.entity.CardType;
import bumblebee.xchangepass.domain.card.repository.CardRepository;
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

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
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

    @Autowired
    private CardRepository cardRepository;

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
    @DisplayName("카드 정보 조회 시 키 복호화 및 Redis 캐시 저장 확인")
    void verifyKeyDecryptionAndRedisStorage() {
        Long userId = 2L;

        cardService.generatePhysicalCard(userId);

        User user = userRepository.findByUserId(userId)
                .orElseThrow(ErrorCode.USER_NOT_FOUND::commonException);

        Card card = user.getWallet().getCards().stream()
                .findFirst()
                .orElseThrow(ErrorCode.CARD_NOT_FOUND::commonException);

        Long cardId = card.getCardId();

        DetailedCardInfoResponse cachedCardBefore = cardCacheService.getCardInfo(cardId);
        assertNull(cachedCardBefore, "Redis에 카드 정보 조회 X");

        DetailedCardInfoResponse detailedCardInfo = cardService.getDetailedCardInfo(cardId);
        assertNotNull(detailedCardInfo, "복호화된 카드 정보 조회 가능");

        SecretKey decryptedAESKey = rsaEncryption.decryptAESKeyWithKMS(card.getEncryptionData().getEncryptedAesKey());

        String decryptedCardNumber = AESEncryption.decryptData(
                card.getCardNumber(), decryptedAESKey, card.getEncryptionData().getIv());

        String decryptedCvc = AESEncryption.decryptData(
                card.getCvc(), decryptedAESKey, card.getEncryptionData().getIv());

        assertEquals(decryptedCardNumber, detailedCardInfo.cardNumber(), "복호화된 카드 번호 일치");
        assertEquals(decryptedCvc, detailedCardInfo.cvc(), "복호화된 CVC 일치");

        DetailedCardInfoResponse cachedCardAfter = cardCacheService.getCardInfo(cardId);
        assertNotNull(cachedCardAfter, "Redis에 카드 정보 저장");
        assertEquals(detailedCardInfo, cachedCardAfter, "Redis에 저장된 카드 정보가 복호화된 정보와 일치");
    }

    @Test
    @DisplayName("카드 상태 변경 시 DB와 Redis 동시 반영")
    void changeCardStatus_shouldUpdateBothDatabaseAndRedisCache() {
        Long userId = 2L;
        cardService.generatePhysicalCard(userId);

        List<BasicCardInfoResponse> cardInfo = cardService.getBasicCardInfo(userId);

        Long physicalCardIds = cardInfo.stream()
                                       .filter(c -> c.cardType().equals(CardType.PHYSICAL))
                                       .map(BasicCardInfoResponse::cardId)
                                       .findFirst()
                                       .orElseThrow(ErrorCode.CARD_NOT_FOUND::commonException);



        var request = ChangeCardStatusRequest.builder()
                                             .cardType(CardType.PHYSICAL)
                                             .status(CardStatus.INACTIVE)
                                             .build();

        cardService.changeCardStatus(userId, request);

        Card updatedCard = cardRepository.findById(physicalCardIds)
                .stream()
                .filter(c -> c.getCardId().equals(physicalCardIds))
                .findFirst()
                .orElseThrow();

        assertThat(updatedCard.getCardStatus())
                .as("DB에 저장된 카드 상태 INACTIVE로 변경")
                .isEqualTo(CardStatus.INACTIVE);

        DetailedCardInfoResponse cachedCard = cardCacheService.getCardInfo(physicalCardIds);
        assertThat(cachedCard).isNotNull();
        assertThat(cachedCard.cardStatus())
                .as("Redis 캐시에 저장된 카드 상태 INACTIVE")
                .isEqualTo(CardStatus.INACTIVE);
    }

}
