package bumblebee.xchangepass.domain.wallet.controller;

import bumblebee.xchangepass.domain.wallet.dto.request.WalletChargeRequest;
import bumblebee.xchangepass.domain.wallet.dto.request.WalletCreateRequest;
import bumblebee.xchangepass.domain.wallet.dto.request.WalletTransferRequest;
import bumblebee.xchangepass.domain.wallet.dto.response.WalletBalanceResponse;
import bumblebee.xchangepass.domain.wallet.dto.response.WalletTransactionResponse;
import bumblebee.xchangepass.domain.wallet.service.NamedLockWalletFacade;
import bumblebee.xchangepass.domain.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/wallet")
public class WalletController {

    private final WalletService walletService;
    private final NamedLockWalletFacade namedLockService;

    @PostMapping("/create")
    @ResponseStatus(HttpStatus.CREATED)
    public void create(@RequestBody WalletCreateRequest request){
        walletService.createWallet(request.userId());
    }

    @GetMapping("/transaction")
    @ResponseStatus(HttpStatus.OK)
    public List<WalletTransactionResponse> transaction(@RequestParam Long userId) {
        return walletService.transaction(userId);
    }

    @PostMapping("/charge")
    @ResponseStatus(HttpStatus.CREATED)
    public void charge(@RequestBody WalletChargeRequest request) {
        namedLockService.charge(request);
    }

    @PutMapping("/withdraw")
    @ResponseStatus(HttpStatus.OK)
    public BigDecimal withdrawal(@RequestBody WalletChargeRequest request) {
        return namedLockService.withdrawal(request);
    }

    @PutMapping("/transfer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void transfer(@RequestBody WalletTransferRequest request) {
        namedLockService.transfer(request);
    }

    @GetMapping("/balance")
    @ResponseStatus(HttpStatus.OK)
    public List<WalletBalanceResponse> balance(@RequestParam Long userId) {
        return walletService.balance(userId);
    }


}
