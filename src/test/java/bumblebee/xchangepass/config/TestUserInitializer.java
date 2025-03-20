package bumblebee.xchangepass.config;

import bumblebee.xchangepass.domain.user.dto.request.UserRegisterRequest;
import bumblebee.xchangepass.domain.user.entity.Sex;
import bumblebee.xchangepass.domain.user.repository.UserRepository;
import bumblebee.xchangepass.domain.user.service.UserRegisterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.event.EventListener;

import java.util.stream.IntStream;

@TestConfiguration
public class TestUserInitializer {

    @Autowired
    private UserRegisterService userService;

    @Autowired
    private UserRepository userRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void initTestUsers() {
        userRepository.deleteAll();

        IntStream.rangeClosed(1, 5).forEach(i -> {
            UserRegisterRequest testUser = UserRegisterRequest.builder()
                    .userEmail("Test" + i + "@gmail.com")
                    .userPwd("Qwer1234!")
                    .userName("테스터" + i)
                    .userPhoneNumber("010-0000-000" + i)
                    .userSex(i >= 4 ? Sex.FEMALE : Sex.MALE)
                    .walletPassword("1234")
                    .build();

            userService.signupUser(testUser);
        });
    }
}

