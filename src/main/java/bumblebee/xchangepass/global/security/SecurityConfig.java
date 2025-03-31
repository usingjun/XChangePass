package bumblebee.xchangepass.global.security;

import bumblebee.xchangepass.global.security.handler.CustomAccessDeniedHandler;
import bumblebee.xchangepass.global.security.handler.CustomAuthenticationEntryPointHandler;
import bumblebee.xchangepass.global.security.jwt.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthFilter jwtAuthFilter;  // ✅ 기존 `SecurityConfig`에서 유지
    private final CustomAuthenticationEntryPointHandler customAuthenticationEntryPointHandler;  // ✅ 기존 `SecurityConfig`에서 유지
    private final CustomAccessDeniedHandler customAccessDeniedHandler;  // ✅ 기존 `SecurityConfig`에서 유지

    @Value("${cors.url}")
    private String corsUrl;  // ✅ 기존 `SpringSecurityConfig`에서 유지

    @Value("${cors.front.url}")
    private String frontUrl;  // ✅ 기존 `SpringSecurityConfig`에서 유지

    /**
     * 🔒 비밀번호 암호화 설정 (BCrypt 사용)
     */
    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 🎭 역할(Role) 계층 구조 설정
     * ✅ 기존 `SpringSecurityConfig`에서 유지
     */
    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("""
               ROLE_ADMIN > ROLE_USER
               """);
    }

    /**
     * 🔥 Spring Security 설정 통합
     * ✅ 기존 `SpringSecurityConfig`의 CORS 및 기본 설정 유지
     * ✅ 기존 `SecurityConfig`의 JWT 필터 및 예외 처리 유지
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        System.out.println("security start");
        http
                .csrf(AbstractHttpConfigurer::disable) // ✅ 기존 `SpringSecurityConfig` & `SecurityConfig`에서 유지 (CSRF 비활성화)
                .cors(corsCustomizer -> corsCustomizer.configurationSource(corsConfigurationSource())) // ✅ 기존 `SpringSecurityConfig`에서 유지 (CORS 설정)
                .formLogin(AbstractHttpConfigurer::disable) // ✅ 기존 `SpringSecurityConfig` & `SecurityConfig`에서 유지 (Form 로그인 비활성화)
                .logout(AbstractHttpConfigurer::disable) // ✅ 기존 `SpringSecurityConfig` & `SecurityConfig`에서 유지 (로그아웃 비활성화)
                .httpBasic(AbstractHttpConfigurer::disable) // ✅ 기존 `SpringSecurityConfig`에서 유지 (HTTP Basic 인증 비활성화)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // ✅ 기존 `SpringSecurityConfig` & `SecurityConfig`에서 유지 (세션 미사용, JWT 사용)

                // 🔹 경로별 인가 작업
                .authorizeHttpRequests(auth -> auth
                        // 정적 리소스 및 공통 경로 허용
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/static/**", "/index.html").permitAll()
                        .requestMatchers("/login", "/api/v1/user/signup", "/token-refresh", "/favicon.ico", "/error").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui", "/v3/api-docs/**", "/v3/api-docs").permitAll() //swagger-ui

                        // 🔓 환율 조회는 인증 없이 허용
                        .requestMatchers("/api/exchange-rate/**").permitAll()
                        // 관리자만 사용자 삭제 가능
                        .requestMatchers("/user").hasRole("ADMIN")

                        // 그 외 모든 요청은 인증 필요
                        .anyRequest().authenticated())

                // 🔥 JWT 필터 추가 (UsernamePasswordAuthenticationFilter 앞에 배치)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

                // ⚠️ 예외 처리 핸들러 추가
                .exceptionHandling(conf -> conf
                        .authenticationEntryPoint(customAuthenticationEntryPointHandler)
                        .accessDeniedHandler(customAccessDeniedHandler));

        return http.build();
    }

    /**
     * 🌍 CORS 설정
     * ✅ 기존 `SpringSecurityConfig`에서 유지하며 `Bean`으로 관리
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        return request -> {
            CorsConfiguration configuration = new CorsConfiguration();
            configuration.setAllowedOrigins(List.of(frontUrl, corsUrl));
            configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
            configuration.setAllowedHeaders(Arrays.asList("Content-Type", "Authorization", "X-Requested-With", "Accept", "Origin", "Access-Control-Allow-Origin"));
            configuration.setAllowCredentials(true);
            configuration.setMaxAge(3600L);
            configuration.setExposedHeaders(Arrays.asList("Authorization", "Set-Cookie", "refresh"));
            return configuration;
        };
    }
}
