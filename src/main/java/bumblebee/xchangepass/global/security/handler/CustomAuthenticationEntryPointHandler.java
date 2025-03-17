package bumblebee.xchangepass.global.security.handler;

import bumblebee.xchangepass.global.error.ErrorCode;
import com.google.gson.JsonObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * Custom Authentication Entry Point Handler
 */
@Slf4j
@Component
public class CustomAuthenticationEntryPointHandler implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException {
        log.info("[CustomAuthenticationEntryPointHandler] :: {}", authException.getMessage());
        log.info("[CustomAuthenticationEntryPointHandler] :: {}", request.getRequestURL());
        log.info("[CustomAuthenticationEntryPointHandler] :: 토큰 정보가 만료되었거나 존재하지 않음");

        response.setStatus(ErrorCode.ACCESS_DENIED.getStatus().value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=UTF-8");

        JsonObject returnJson = new JsonObject();
        returnJson.addProperty("errorCode", ErrorCode.ACCESS_DENIED.getCode());
        returnJson.addProperty("errorMsg", ErrorCode.ACCESS_DENIED.getMessage());

        PrintWriter out = response.getWriter();
        out.print(returnJson);
    }

}