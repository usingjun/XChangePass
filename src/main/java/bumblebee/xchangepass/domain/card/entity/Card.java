package bumblebee.xchangepass.domain.card.entity;

import jakarta.persistence.*;
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

    @Column(name = "card_number")
    private String cardNumber;

    @Column(name = "cvc")
    private Integer cvc;

    @Column(name = "owner_name")
    private String ownerName;

    @Column(name = "card_type")
    @Enumerated(value = EnumType.STRING)
    private CardType cardType;

    @Column(name = "card_status")
    @Enumerated(value = EnumType.STRING)
    private CardStatus cardStatus;

    @Column(name = "expiration_date")
    private LocalDateTime expirationDate;

    @CreatedDate
    @Column(name = "card_create_date")
    private LocalDateTime cardCreateDate;
}
