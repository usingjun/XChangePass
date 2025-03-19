package bumblebee.xchangepass.domain.card.service;

import bumblebee.xchangepass.domain.card.repository.CardRepository;
import org.springframework.stereotype.Component;

import java.util.Random;

@Component
public class CardFactory {
    private static final Random RANDOM = new Random();

    /**
     * 16자리 카드 번호를 생성 (####-####-####-#### 형식)
     */
    public String generateCardNumber() {
        return String.format("%04d-%04d-%04d-%04d",
                RANDOM.nextInt(10000),
                RANDOM.nextInt(10000),
                RANDOM.nextInt(10000),
                RANDOM.nextInt(10000));
    }

    /**
     * 3자리 CVC 번호를 생성 (000 ~ 999)
     */
    public String generateCvc() {
        return String.format("%03d", RANDOM.nextInt(1000));
    }

    /**
     * 중복되지 않는 카드 번호를 생성 (DB 중복 체크 포함)
     */
    public String generateUniqueCardNumber(CardRepository cardRepository) {
        String cardNumber;
        do {
            cardNumber = generateCardNumber();
        } while (cardRepository.existsByCardNumber(cardNumber));
        return cardNumber;
    }
}
