package com.example.stockbrokerage.controller;

import com.example.stockbrokerage.dto.LoginRequest;
import com.example.stockbrokerage.dto.LoginResponse;
import com.example.stockbrokerage.entity.User;
import com.example.stockbrokerage.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@Tag(name = "Authentication", description = "Login and session management")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @SecurityRequirements   // login endpoint requires no auth
    @Operation(
        summary = "Login",
        description = "Authenticate with username and password. Returns client ID and role used for subsequent requests."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login successful",
            content = @Content(schema = @Schema(implementation = LoginResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid credentials", content = @Content)
    })
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        try {
            User user = authService.authenticate(request.getUsername(), request.getPassword());
            return ResponseEntity.ok(LoginResponse.fromUser(user));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Invalidate the current session.")
    @ApiResponse(responseCode = "200", description = "Logged out successfully")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.ok().build();
    }
}
