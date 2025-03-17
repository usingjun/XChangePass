package bumblebee.xchangepass.global.security.v1.login;

import bumblebee.xchangepass.domain.user.dto.request.UserRegisterRequest;
import bumblebee.xchangepass.domain.user.repository.UserRepository;
import bumblebee.xchangepass.domain.user.service.NicknameGenerator;
import bumblebee.xchangepass.global.error.ErrorCode;
import bumblebee.xchangepass.global.exception.CommonException;
import bumblebee.xchangepass.global.util.DuplicateKeyExceptionHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserRegisterService {

    private final UserRepository userRepository;
    private final NicknameGenerator nicknameGenerator;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    /**
     * ✅ 사용자 등록
     * 닉네임 Redis INCR을 활요한 자동 생성
     * 실명의 경우 추후 전화번호, 이메일에서 받아 오는 형식으로 변경 예정
     */
    public void signupUser(UserRegisterRequest request) {
        String uniqueNickname = null;
        try{
            uniqueNickname = nicknameGenerator.generateUniqueNickname();
            userRepository.save(request.toEntity(bCryptPasswordEncoder, uniqueNickname));
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
}
