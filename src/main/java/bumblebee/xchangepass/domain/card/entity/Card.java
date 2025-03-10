package bumblebee.xchangepass.domain.card.entity;

import bumblebee.xchangepass.global.common.EncryptionData;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Getter
@Table(name = "card")
@NoArgsConstructor(access = PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "card_id", nullable = false)
    private Long cardId;

    @Column(name = "card_number", nullable = false)
    private String cardNumber;

    @Column(name = "cvc", nullable = false)
    private String cvc;

    @Column(name = "card_password", nullable = false)
    private String cardPassword;

    @Column(name = "owner_name", nullable = false)
    private String ownerName;

    @Column(name = "card_type", nullable = false)
    @Enumerated(value = EnumType.STRING)
    private CardType cardType;

    @Column(name = "card_status", nullable = false)
    @Enumerated(value = EnumType.STRING)
    private CardStatus cardStatus;

    @Column(name = "expiration_date", nullable = false)
    private LocalDateTime expirationDate;

    @CreatedDate
    @Column(name = "card_create_date")
    private LocalDateTime cardCreateDate;

    @Embedded
    private EncryptionData encryptionData;

    @Builder
    public Card(String cardNumber,
                String cvc,
                String cardPassword,
                String ownerName,
                CardType cardType,
                String encryptedAesKey,
                byte[] iv){
        this.cardNumber = cardNumber;
        this.cvc = cvc;
        this.cardPassword = cardPassword;
        this.ownerName = ownerName;
        this.cardType = cardType;
        this.cardStatus = CardStatus.ACTIVE;
        this.expirationDate = LocalDateTime.now().plusYears(5);
        this.encryptionData = new EncryptionData(encryptedAesKey, iv);
    }

}
