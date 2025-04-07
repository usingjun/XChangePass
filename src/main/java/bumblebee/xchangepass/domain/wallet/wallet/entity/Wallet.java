package bumblebee.xchangepass.domain.wallet.wallet.entity;

import bumblebee.xchangepass.domain.card.entity.Card;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.wallet.balance.entity.WalletBalance;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "wallet")
@Getter
@NoArgsConstructor()
@EntityListeners(AuditingEntityListener.class)
public class Wallet {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "wallet_id", nullable = false)
    private Long walletId;

    @Column(name = "wallet_password", nullable = false)
    private String walletPassword;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @OneToMany(mappedBy = "wallet", fetch = FetchType.LAZY, orphanRemoval = true, cascade = CascadeType.ALL)
    private List<Card> cards;

    @OneToMany(mappedBy = "wallet", fetch = FetchType.LAZY, orphanRemoval = true, cascade = CascadeType.ALL)
    private List<WalletBalance> walletBalances;

    @CreatedDate
    private LocalDateTime walletCreatedAt;

    public Wallet(User user, String walletPassword) {
        this.user = user;
        this.walletPassword = walletPassword;
    }
}