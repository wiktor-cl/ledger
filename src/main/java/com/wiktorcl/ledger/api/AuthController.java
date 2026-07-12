package com.wiktorcl.ledger.api;

import com.wiktorcl.ledger.api.dto.AuthResponse;
import com.wiktorcl.ledger.api.dto.LoginRequest;
import com.wiktorcl.ledger.api.dto.RegisterRequest;
import com.wiktorcl.ledger.security.AuthService;
import com.wiktorcl.ledger.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        JwtService.GeneratedToken token = authService.register(request.username(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(new AuthResponse(token.token(), token.expiresAt()));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        JwtService.GeneratedToken token = authService.login(request.username(), request.password());
        return new AuthResponse(token.token(), token.expiresAt());
    }
}
