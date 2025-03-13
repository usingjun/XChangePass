package bumblebee.xchangepass.global.security;

import bumblebee.xchangepass.global.security.v1.LoginService;
import bumblebee.xchangepass.global.security.v1.login.dto.LoginRequest;
import bumblebee.xchangepass.global.security.v1.login.dto.LoginResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class LoginController {

    private final LoginService loginService;

    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/login")
    public LoginResponse memberLogin(@RequestBody @Valid LoginRequest loginRequest) {
        return loginService.login(loginRequest);
    }
}
