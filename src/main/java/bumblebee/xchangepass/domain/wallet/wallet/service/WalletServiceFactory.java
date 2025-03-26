package bumblebee.xchangepass.domain.wallet.wallet.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class WalletServiceFactory {
    private final Map<String, WalletService> walletServices;

    @Autowired
    public WalletServiceFactory(List<WalletService> walletServiceList) {
        this.walletServices = walletServiceList.stream()
                .collect(Collectors.toMap(WalletService::getType, Function.identity()));
    }

    public WalletService getService(String type) {
        return walletServices.get(type);
    }
}
