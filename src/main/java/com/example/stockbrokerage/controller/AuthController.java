package com.example.stockbrokerage.controller;

import com.example.stockbrokerage.dto.LoginRequest;
import com.example.stockbrokerage.dto.LoginResponse;
import com.example.stockbrokerage.entity.User;
import com.example.stockbrokerage.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class AuthController {
    
    private final AuthService authService;
    
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        try {
            User user = authService.authenticate(request.getUsername(), request.getPassword());
            return ResponseEntity.ok(LoginResponse.fromUser(user));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.ok().build();
    }
}
