package bumblebee.xchangepass.domain.wallet.wallet.service;

import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletInOutRequest;
import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletTransferRequest;
import bumblebee.xchangepass.domain.wallet.wallet.dto.response.WalletBalanceResponse;

import java.math.BigDecimal;
import java.util.List;


public interface WalletService {

    void charge(Long userId, WalletInOutRequest request);

    BigDecimal withdrawal(Long userId, WalletInOutRequest request);

    void transfer(Long senderId, WalletTransferRequest request);

    List<WalletBalanceResponse> balance(Long userId);
}
