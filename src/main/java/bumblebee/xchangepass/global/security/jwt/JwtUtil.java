package bumblebee.xchangepass.global.security.jwt;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;

@UtilityClass
@Slf4j
public class JwtUtil {

    /**
     * Spring Security Context에서 로그인한 사용자의 id 조회
     *
     * @param authentication Authentication
     * @return 로그인한 사용자의 id
     * @throws AccessDeniedException AccessDeniedException
     */
    public Long getLoginId(final Authentication authentication) {
        Object principal = authentication.getPrincipal();

        if (principal instanceof Long id) {
            return id;
        }

        if (principal instanceof UserDetails userDetails) {
            return Long.parseLong(userDetails.getUsername());
        }

        throw new AccessDeniedException("Invalid authentication principal");
    }

    /**
     * 정상적으로 로그인한 사용자 정보인지 체크
     *
     * @param authentication Authentication
     * @throws AccessDeniedException AccessDeniedException
     */
    private void checkAuth(final Authentication authentication) throws AccessDeniedException {
        if(authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("로그인 정보가 존재하지 않습니다.");
        }
    }

}
