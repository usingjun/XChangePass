package bumblebee.xchangepass.domain.wallet.wallet.service.impl;

import bumblebee.xchangepass.domain.card.service.CardService;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.wallet.balance.service.WalletBalanceService;
import bumblebee.xchangepass.domain.wallet.wallet.dto.WalletPasswordResponse;
import bumblebee.xchangepass.domain.wallet.wallet.entity.Wallet;
import bumblebee.xchangepass.domain.wallet.wallet.repository.WalletRepository;
import bumblebee.xchangepass.global.error.ErrorCode;
import bumblebee.xchangepass.global.exception.CommonException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Currency;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl {

    private final WalletRepository walletRepository;
    private final CardService cardService;
    private final WalletBalanceService balanceService;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    @Transactional
    public void createWallet(User user, String walletPassword) {
        Wallet wallet = new Wallet(user, walletPassword);

        user.changeWallet(walletRepository.save(wallet));
        balanceService.createBalance(wallet, Currency.getInstance("KRW"));

        // ✅ 모바일 카드 발급 (동기 처리)
        cardService.generateMobileCard(wallet);
    }

    public WalletPasswordResponse checkWalletPassword(Long userId, String rawPassword) {
        try {
            Wallet wallet = walletRepository.findByUserId(userId)
                    .orElseThrow(ErrorCode.WALLET_NOT_FOUND::commonException);

            if (!bCryptPasswordEncoder.matches(rawPassword, wallet.getWalletPassword())) {
                throw new CommonException(ErrorCode.INVALID_WALLET_PASSWORD);
            }

            return new WalletPasswordResponse(true); // ✅ 일치
        } catch (CommonException e) {
            return new WalletPasswordResponse(false); // ✅ 불일치
        }
    }

}
