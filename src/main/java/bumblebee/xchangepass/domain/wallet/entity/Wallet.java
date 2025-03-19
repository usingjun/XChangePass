package bumblebee.xchangepass.domain.wallet.entity;

import bumblebee.xchangepass.domain.card.entity.Card;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.walletBalance.entity.WalletBalance;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.List;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "wallet")
@Getter
@NoArgsConstructor()
@EntityListeners(AuditingEntityListener.class)
public class Wallet {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "wallet_id", nullable = false)
    public Long walletId;

    @Column(name = "wallet_password", nullable = false)
    public String walletPassword;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    public User user;

    @OneToMany(mappedBy = "wallet", fetch = FetchType.LAZY, orphanRemoval = true, cascade = CascadeType.ALL)
    List<Card> cards;

    @OneToMany(mappedBy = "wallet", fetch = FetchType.LAZY, orphanRemoval = true, cascade = CascadeType.ALL)
    List<WalletBalance> walletBalances;

    @CreatedDate
    public LocalDateTime walletCreatedAt;

    public Wallet(User user, String walletPassword) {
        this.user = user;
        this.walletPassword = walletPassword;
    }
}