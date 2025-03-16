package bumblebee.xchangepass.global.security.jwt;

import bumblebee.xchangepass.domain.user.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;

    private final UserService userService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        final String token = request.getHeader("Authorization");

        String userId = null;
        System.out.println("dofilter start");

        // Bearer token 검증 후 user name 조회
        if(token != null && !token.isEmpty()) {
            String jwtToken = token.substring(7);

            userId = jwtProvider.getUserIdFromToken(jwtToken);
        }

        System.out.println("userId = " + userId);

        // token 검증 완료 후 SecurityContextHolder 내 인증 정보가 없는 경우 저장
        if(userId != null && !userId.isEmpty() && SecurityContextHolder.getContext().getAuthentication() == null) {
            // Spring Security Context Holder 인증 정보 set
            SecurityContextHolder.getContext().setAuthentication(getUserAuth(userId));
        }

        System.out.println("dofilter end");
        filterChain.doFilter(request,response);
    }

    /**
     * token의 사용자 idx를 이용하여 사용자 정보 조회하고, UsernamePasswordAuthenticationToken 생성
     *
     * @param userEmail 사용자 email
     * @return 사용자 UsernamePasswordAuthenticationToken
     */
    private UsernamePasswordAuthenticationToken getUserAuth(String userEmail) {
        var userInfo = userService.readUserByUserId(userEmail);

        return new UsernamePasswordAuthenticationToken(userInfo.userId(),
                userInfo.password(),
                Collections.singleton(new SimpleGrantedAuthority(userInfo.role().toString()))
        );
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {

        //swagger-ui
        if (request.getRequestURI().startsWith("/v3/api-docs")||request.getRequestURI().startsWith("/swagger-ui")) {
            return true;
        }

        // 예외적으로 필터링하지 않을 경로들
        List<String> excludedPaths = List.of(
                "/",
                "/health",
                "/index.html",
                "/static/**",
                "/css/**",
                "/js/**",
                "/images/**",
                "/login",
                "/api/v1/signup"
        );

        AntPathMatcher pathMatcher = new AntPathMatcher();
        return excludedPaths.stream().anyMatch(pattern -> pathMatcher.match(pattern, request.getRequestURI()));
    }

}