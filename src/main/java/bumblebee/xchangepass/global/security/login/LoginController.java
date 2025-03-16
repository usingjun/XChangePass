package bumblebee.xchangepass.global.security.login;

import bumblebee.xchangepass.global.security.login.dto.LoginRequest;
import bumblebee.xchangepass.global.security.login.dto.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "Login", description = "Login API")
public class LoginController {

    private final LoginService loginService;

    @Operation(summary = "로그인", description = "로그인합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "로그인 성공", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "400", description = "파라미터 누락",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\n  \"code\": \"S003\"," +
                                                              "\n  \"message\": \"아이디 혹은 비밀번호를 입력하세요.\"}"))
            ),
            @ApiResponse(responseCode = "401", description = "아이디 혹은 비밀번호가 일치하지 않습니다.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\n  \"code\": \"S002\"," +
                                                              "\n  \"message\": \"아이디 혹은 비밀번호가 일치하지 않습니다.\"}"))
            )
    })
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/login")
    public LoginResponse memberLogin(@RequestBody @Valid LoginRequest loginRequest) {
        return loginService.login(loginRequest);
    }

    @Operation(summary = "로그아웃", description = "로그아웃합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "로그아웃 성공", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 Refresh Token",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\n  \"code\": \"S004\"," +
                                                              "\n  \"message\": \"유효하지 않은 Refresh Token입니다.\"}")))
    })
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/logout")
    public void logout(@RequestHeader("Authorization") String refreshToken) {
        // Refresh Token 삭제
        loginService.logout(refreshToken);
    }
}
