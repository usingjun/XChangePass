package bumblebee.xchangepass.domain.wallet.wallet.scheduler;

import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletTransferRequest;
import bumblebee.xchangepass.domain.wallet.wallet.entity.ScheduledTransfer;
import bumblebee.xchangepass.domain.wallet.wallet.entity.WalletTransferType;
import bumblebee.xchangepass.domain.wallet.wallet.repository.ScheduledTransferRepository;
import bumblebee.xchangepass.domain.wallet.wallet.service.WalletService;
import bumblebee.xchangepass.domain.wallet.wallet.service.WalletServiceFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static bumblebee.xchangepass.domain.wallet.wallet.entity.WalletTransferStatus.*;

@Slf4j
@Service
public class ScheduledTransferService {
    @Autowired
    ScheduledTransferRepository repository;

    @Autowired
    WalletServiceFactory walletServiceFactory;

    @Autowired
    ScheduledTransferRepository scheduledTransferRepository;

    @Scheduled(fixedDelay = 60000) // 1분마다 실행
    @Transactional
    public void processScheduledTransfers() {
        LocalDateTime now = LocalDateTime.now();
        List<ScheduledTransfer> pendingList =
                repository.findByStatusAndScheduledAtBefore(PENDING, now);

        for (ScheduledTransfer scheduled : pendingList) {
            try {
                WalletTransferRequest request = new WalletTransferRequest(
                        scheduled.getReceiverName(),
                        scheduled.getReceiverPhoneNumber(),
                        scheduled.getTransferAmount(),
                        scheduled.getFromCurrency(),
                        scheduled.getToCurrency(),
                        scheduled.getScheduledAt(),
                        WalletTransferType.GENERAL
                );
                walletServiceFactory.getService("namedLock").transfer(scheduled.getSenderId(), request);
                scheduled.markSuccess();
            } catch (Exception e) {
                scheduled.markFailed();
                log.error("예약 송금 실패: {}", scheduled.getScheduledTransferId(), e);
            }
        }
    }

    public void saveSchedule(Long senderId, WalletTransferRequest request) {
        ScheduledTransfer entity = new ScheduledTransfer(senderId,request);
        scheduledTransferRepository.save(entity);
    }
}
