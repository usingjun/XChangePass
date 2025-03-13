package bumblebee.xchangepass.domain.user.service;

import bumblebee.xchangepass.domain.user.dto.request.UserRegisterRequest;
import bumblebee.xchangepass.domain.user.dto.request.UserUpdateRequest;
import bumblebee.xchangepass.domain.user.dto.response.UserResponse;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.user.repository.UserRepository;
import bumblebee.xchangepass.domain.wallet.repository.WalletRepository;
import bumblebee.xchangepass.domain.wallet.service.WalletService;
import bumblebee.xchangepass.global.error.ErrorCode;
import bumblebee.xchangepass.global.exception.CommonException;
import bumblebee.xchangepass.global.util.DuplicateKeyExceptionHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final WalletService walletService;
    private final NicknameGenerator nicknameGenerator;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final PasswordEncoder passwordEncoder;

    /**
     * ✅ 사용자 등록
     * 닉네임 Redis INCR을 활요한 자동 생성
     * 실명의 경우 추후 전화번호, 이메일에서 받아 오는 형식으로 변경 예정
     */
    @Transactional
    public void signupUser(UserRegisterRequest request) {
        String uniqueNickname = null;
        try{
            uniqueNickname = nicknameGenerator.generateUniqueNickname();
            User createUser = userRepository.save(request.toEntity(bCryptPasswordEncoder, uniqueNickname));
            userRepository.flush();

            // ✅ 지갑 생성 (동기 처리)
            walletService.createWallet(createUser, passwordEncoder.encode(request.walletPassword()));
        } catch (DataIntegrityViolationException e) {
            nicknameGenerator.rollbackNicknameId(uniqueNickname);
            DuplicateKeyExceptionHandler.handle(e);
        } catch (IllegalArgumentException | CommonException e) {
            nicknameGenerator.rollbackNicknameId(uniqueNickname);
            throw e;
        } catch (Exception e) {
            nicknameGenerator.rollbackNicknameId(uniqueNickname);
            throw ErrorCode.USER_NOT_REGISTER.commonException();
        }
    }

    /**
     * ✅ 사용자 조회
     */
    public UserResponse readUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(ErrorCode.USER_NOT_FOUND::commonException);

        return new UserResponse(user);
    }

    /**
     * ✅ 사용자 정보 수정
     */
    public void updateUser(Long userId, UserUpdateRequest request) {

        if (request.userNickname().startsWith("User_")) {
            throw ErrorCode.INVALID_NICKNAME_PREFIX.commonException();
        }

        userRepository.checkForDuplicateNickname(request.userNickname(), userId);

        try {
            userRepository.updateUser(request, userId);
        } catch (DataIntegrityViolationException e) {
            DuplicateKeyExceptionHandler.handle(e);
        } catch (CommonException e) {
            throw e;
        } catch (Exception e) {
            throw ErrorCode.USER_NOT_MODIFY.commonException();
        }
    }

    /**
     * ✅ 사용자 삭제 요청 (Soft Delete)
     */
    @Transactional
    public void softDeleteUser(Long userId) {
        User existUser = userRepository.findById(userId)
                .orElseThrow(ErrorCode.USER_NOT_FOUND::commonException);

        existUser.softDelete();
    }

    /**
     * ✅ 사용자 삭제 (Hard Delete)
     * 트랜잭션 관리를 위해 비동기 처리와 따로 분리
     */
    @Transactional
    public void deleteUserBatch(LocalDateTime thirtyDaysAgo) {
        userRepository.deleteOldUsers(thirtyDaysAgo);
    }
}
