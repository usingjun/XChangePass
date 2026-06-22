package bumblebee.xchangepass.domain.wallet.wallet.service;

import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletInOutRequest;
import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletTransferRequest;
import bumblebee.xchangepass.domain.wallet.wallet.dto.response.WalletBalanceResponse;
import bumblebee.xchangepass.domain.wallet.transfer.dto.WalletTransferResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;


public interface WalletService {

    void charge(Long userId, WalletInOutRequest request);

    BigDecimal withdrawal(Long userId, WalletInOutRequest request);

    void transfer(Long senderId, WalletTransferRequest request);

    WalletTransferResponse transfer(UUID transferId, Long senderId, Long receiverId,
                                    WalletTransferRequest request);

    List<WalletBalanceResponse> balance(Long userId);
}
