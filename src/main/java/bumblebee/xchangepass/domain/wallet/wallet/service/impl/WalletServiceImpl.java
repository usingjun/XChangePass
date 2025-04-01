package bumblebee.xchangepass.domain.wallet.wallet.service.impl;

import bumblebee.xchangepass.domain.card.service.CardService;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.wallet.balance.service.WalletBalanceService;
import bumblebee.xchangepass.domain.wallet.wallet.entity.Wallet;
import bumblebee.xchangepass.domain.wallet.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Currency;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl {

    private final WalletRepository walletRepository;
    private final CardService cardService;
    private final WalletBalanceService balanceService;

    @Transactional
    public void createWallet(User user, String walletPassword) {
        Wallet wallet = new Wallet(user, walletPassword);

        user.changeWallet(walletRepository.save(wallet));
        balanceService.createBalance(wallet, Currency.getInstance("KRW"));

        // ✅ 모바일 카드 발급 (동기 처리)
        cardService.generateMobileCard(wallet);
    }
}
