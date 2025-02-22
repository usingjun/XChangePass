package bumblebee.xchangepass.domain.user.service;

import bumblebee.xchangepass.domain.user.dto.request.UserRegisterRequest;
import bumblebee.xchangepass.domain.user.dto.request.UserUpdateRequest;
import bumblebee.xchangepass.domain.user.dto.response.UserResponse;
import bumblebee.xchangepass.domain.user.entity.User;
import bumblebee.xchangepass.domain.user.repository.UserRepository;
import bumblebee.xchangepass.global.error.ErrorCode;
import bumblebee.xchangepass.global.exception.CommonException;
import bumblebee.xchangepass.global.util.DuplicateKeyExceptionHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    /*
    사용자 등록
    실명의 경우 전화번호, 이메일 인증 시 받아오는 방식으로 구상 생각
     */
    public void signupUser(UserRegisterRequest request) {
        try{
            userRepository.save(request.toEntity(bCryptPasswordEncoder));
        }catch (DataIntegrityViolationException e) {
            DuplicateKeyExceptionHandler.handle(e);
        } catch (Exception e) {
            throw ErrorCode.USER_NOT_REGISTER.commonException();
        }
    }

    /*
    사용자 조회
     */
    public UserResponse readUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(ErrorCode.USER_NOT_FOUND::commonException);

        return new UserResponse(user);
    }

    /*
    사용자 정보 수정
    전화번호 변경의 경우 휴대폰 인증 진행 후 따로 변경 예정
     */
    public void updateUser(Long userId, UserUpdateRequest request) {
        //중복 검사
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

    /*
    사용자 삭제 요청 (Soft Delete)
     */
    public void softDeleteUser(Long userId) {
        User existUser = userRepository.findById(userId)
                .orElseThrow(ErrorCode.USER_NOT_FOUND::commonException);

        existUser.softDelete();
    }
}
