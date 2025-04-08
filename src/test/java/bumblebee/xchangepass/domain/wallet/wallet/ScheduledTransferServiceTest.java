package bumblebee.xchangepass.domain.wallet.wallet;

import bumblebee.xchangepass.domain.user.entity.Sex;
import bumblebee.xchangepass.domain.user.service.UserRegisterService;
import bumblebee.xchangepass.domain.user.service.UserService;
import bumblebee.xchangepass.domain.wallet.wallet.dto.request.WalletTransferRequest;
import bumblebee.xchangepass.domain.wallet.wallet.entity.ScheduledTransfer;
import bumblebee.xchangepass.domain.wallet.wallet.entity.WalletTransferType;
import bumblebee.xchangepass.domain.wallet.wallet.repository.ScheduledTransferRepository;
import bumblebee.xchangepass.domain.wallet.wallet.scheduler.ScheduledTransferService;
import bumblebee.xchangepass.domain.wallet.wallet.service.WalletServiceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class ScheduledTransferServiceTest {

    @Autowired
    private ScheduledTransferService scheduledTransferService;

    @Autowired
    private ScheduledTransferRepository scheduledTransferRepository;

    @Autowired
    private WalletServiceFactory walletServiceFactory;

    @Autowired
    private UserRegisterService userRegisterService;

    @Autowired
    private UserService userService;

    private Long senderId;

    @BeforeEach
    void setUp() {
        // 간단한 사용자 등록
        userRegisterService.signupUser(new bumblebee.xchangepass.domain.user.dto.request.UserRegisterRequest(
                "sender@example.com", "Password123!", "보내는사람", "010-1111-1111", Sex.MALE, "1234"
        ));

        senderId = userService.readUser("보내는사람", "010-1111-1111").getUserId();
    }

    @Test
    void 예약송금_등록_및_처리_정상동작() {
        // 🔹 예약 시간: 현재보다 5초 전
        WalletTransferRequest request = new WalletTransferRequest(
                "받는사람",
                "010-2222-2222",
                BigDecimal.valueOf(1000),
                Currency.getInstance("KRW"),
                Currency.getInstance("KRW"),
                LocalDateTime.now().minusSeconds(5),
                WalletTransferType.SCHEDULE
        );

        // 🔹 예약 송금 등록
        walletServiceFactory.getService("namedLock").transfer(senderId, request);

        List<ScheduledTransfer> list = scheduledTransferRepository.findAll();
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getStatus().name()).isEqualTo("PENDING");

        // 🔹 예약 송금 실행
        scheduledTransferService.processScheduledTransfers();

        ScheduledTransfer after = scheduledTransferRepository.findById(list.get(0).getScheduledTransferId()).orElseThrow();
        assertThat(after.getStatus().name()).isEqualTo("SUCCESS");
    }
}
