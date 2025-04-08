package bumblebee.xchangepass.domain.wallet.wallet.scheduler;

import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletTransferRequest;
import bumblebee.xchangepass.domain.wallet.wallet.entity.ScheduledTransfer;
import bumblebee.xchangepass.domain.wallet.wallet.entity.WalletTransferType;
import bumblebee.xchangepass.domain.wallet.wallet.repository.ScheduledTransferRepository;
import bumblebee.xchangepass.domain.wallet.wallet.service.WalletServiceFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static bumblebee.xchangepass.domain.wallet.wallet.entity.WalletTransferStatus.PENDING;

@Slf4j
@Service
public class ScheduledTransferService {
    @Autowired
    ScheduledTransferRepository repository;

    @Autowired
    ScheduledTransferRepository scheduledTransferRepository;

    @Autowired
    ScheduledTransferExecutor executor;

    @Scheduled(fixedDelay = 60000) // 1분마다 실행
    @Transactional
    public void processScheduledTransfers() {
        List<ScheduledTransfer> pending = repository.findByStatusAndScheduledAtBefore(PENDING, LocalDateTime.now());

        for (ScheduledTransfer scheduled : pending) {
            try {
                executor.execute(scheduled);
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
