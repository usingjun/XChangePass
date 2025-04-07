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
    private final CustomAuthenticationEntryPointHandler customAuthenticationEntryPointHandler;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;

    @Value("${cors.url}")
    private String corsUrl;

    @Value("${cors.front.url}")
    private String frontUrl;

    /**
     * 🔒 비밀번호 암호화 설정 (BCrypt 사용)
     */
    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 🎭 역할(Role) 계층 구조 설정
     */
    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("""
               ROLE_ADMIN > ROLE_USER
               """);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(corsCustomizer -> corsCustomizer.configurationSource(corsConfigurationSource()))
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 🔹 경로별 인가 작업
                .authorizeHttpRequests(auth -> auth
                        // 정적 리소스 및 공통 경로 허용
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/static/**", "/index.html").permitAll()
                        .requestMatchers("/login", "/api/v1/user/signup", "/token-refresh", "/favicon.ico", "/error").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui", "/v3/api-docs/**", "/v3/api-docs").permitAll() //swagger-ui
                        .requestMatchers("/api/v1/card/payment").permitAll()

                        // 🔓 환율 조회는 인증 없이 허용
                        .requestMatchers("/api/exchange-rate/**").permitAll()
                        // 관리자만 사용자 삭제 가능
                        .requestMatchers("/user").hasRole("ADMIN")

                        // 그 외 모든 요청은 인증 필요
                        .anyRequest().authenticated())

                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

                // ⚠️ 예외 처리 핸들러 추가
                .exceptionHandling(conf -> conf
                        .authenticationEntryPoint(customAuthenticationEntryPointHandler)
                        .accessDeniedHandler(customAccessDeniedHandler));

        return http.build();
    }

    /**
     * 🌍 CORS 설정
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
