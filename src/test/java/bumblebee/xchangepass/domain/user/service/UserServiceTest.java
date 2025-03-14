package bumblebee.xchangepass.domain.user.service;

import bumblebee.xchangepass.config.RedisTestBase;
import bumblebee.xchangepass.config.TestUserInitializer;
import bumblebee.xchangepass.domain.user.dto.request.UserUpdateRequest;
import bumblebee.xchangepass.domain.user.entity.Sex;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.user.repository.UserRepository;
import bumblebee.xchangepass.global.error.ErrorCode;
import bumblebee.xchangepass.global.exception.CommonException;
import bumblebee.xchangepass.global.scheduler.UserCleanupScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestUserInitializer.class)
public class UserServiceTest extends RedisTestBase {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserCleanupScheduler userCleanupScheduler;


    @Test
    @DisplayName("회원 정보 수정 & 동적 쿼리 & 예외처리")
    void checkForDuplicateNickname() {
        Long userId1 = 1L;
        Long userId2 = 2L;

        // userId1 사용자의 닉네임을 "Test1"로 변경 (성공)
        UserUpdateRequest firstUpdate = UserUpdateRequest.builder()
                .userNickname("Test1")
                .userAge(23)
                .userSex(Sex.FEMALE)
                .build();
        assertDoesNotThrow(() -> userService.updateUser(userId1, firstUpdate));

        User user1 = userRepository.findById(userId1).orElseThrow();
        assertEquals("Test1", user1.getUserNickname().getValue(), "닉네임이 'Test1'로 업데이트되지 않았습니다.");


        // userId2 사용자가 동일한 "Test1"로 변경 시 예외 발생
        UserUpdateRequest duplicateUpdate = UserUpdateRequest.builder()
                .userNickname("Test1")
                .userAge(23)
                .userSex(Sex.FEMALE)
                .build();

        assertThrows(ErrorCode.USER_DUPLICATE_NICK_NAME.commonException().getClass(), () -> userService.updateUser(userId2, duplicateUpdate));

        // 'User_'로 시작하는 닉네임 변경 시 예외 발생
        UserUpdateRequest invalidPrefixUpdate = UserUpdateRequest.builder()
                .userNickname("User_1000000001")
                .userAge(23)
                .userSex(Sex.FEMALE)
                .build();

        assertThrows(ErrorCode.INVALID_NICKNAME_PREFIX.commonException().getClass(), () -> userService.updateUser(userId2, invalidPrefixUpdate));
    }

    @Test
    @DisplayName("Soft Delete & Hard Delete 테스트")
    void testSoftDeleteUser() {
        Clock mockClock = Clock.fixed(LocalDateTime.now().plusDays(30).atZone(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

        userCleanupScheduler.setClock(mockClock);

        Long userId = 1L;

        User user = userRepository.findById(userId).orElseThrow();
        user.softDelete();
        userRepository.saveAndFlush(user);

        userCleanupScheduler.deleteUser();

        assertThrows(CommonException.class, () -> userRepository.findByUserId(userId).orElseThrow(ErrorCode.USER_NOT_FOUND::commonException));
    }

}
