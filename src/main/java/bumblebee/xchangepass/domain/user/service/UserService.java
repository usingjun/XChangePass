package bumblebee.xchangepass.domain.user.service;

import bumblebee.xchangepass.domain.user.dto.request.UserUpdateRequest;
import bumblebee.xchangepass.domain.user.login.dto.response.UserLoginResponse;
import bumblebee.xchangepass.domain.user.dto.response.UserResponse;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.user.repository.UserRepository;
import bumblebee.xchangepass.global.error.ErrorCode;
import bumblebee.xchangepass.global.exception.CommonException;
import bumblebee.xchangepass.global.util.DuplicateKeyExceptionHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

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

    public UserLoginResponse readUserByUserId(String userId) {
        System.out.println("userId = " + userId);
        User user = userRepository.findByUserId(Long.parseLong(userId))
                .orElseThrow(ErrorCode.USER_NOT_FOUND::commonException);
        System.out.println("user = " + user.toString());
        return UserLoginResponse.fromEntity(user);
    }

    public UserLoginResponse readUserByUserEmail(String userEmail) {
        return UserLoginResponse.fromEntity(userRepository.findByUserEmail(userEmail)
                .orElseThrow(ErrorCode.USER_NOT_FOUND::commonException));
    }

}
