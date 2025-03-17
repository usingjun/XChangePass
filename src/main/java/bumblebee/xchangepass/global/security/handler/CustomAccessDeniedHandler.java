package bumblebee.xchangepass.global.security.handler;

import bumblebee.xchangepass.global.error.ErrorCode;
import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * Custom Access Denied Handler Handler
 */
@Slf4j
@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException, ServletException {
        log.info("[CustomAccessDeniedHandler] :: {}", accessDeniedException.getMessage());
        log.info("[CustomAccessDeniedHandler] :: {}", request.getRequestURL());
        log.info("[CustomAccessDeniedHandler] :: 토큰 정보가 만료되었거나 존재하지 않음");

        response.setStatus(ErrorCode.USER_FORBIDDEN.getStatus().value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=UTF-8");

        JsonObject returnJson = new JsonObject();
        returnJson.addProperty("errorCode", ErrorCode.USER_FORBIDDEN.getCode());
        returnJson.addProperty("errorMsg", ErrorCode.USER_FORBIDDEN.getMessage());

        PrintWriter out = response.getWriter();
        out.print(returnJson);
    }
}
