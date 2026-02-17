package com.example.stockbrokerage.dto;

import com.example.stockbrokerage.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private Long userId;
    private String username;
    private String role;
    private String fullName;
    private String email;
    private Long clientId;
    
    public static LoginResponse fromUser(User user) {
        return new LoginResponse(
            user.getId(),
            user.getUsername(),
            user.getRole().name(),
            user.getFullName(),
            user.getEmail(),
            user.getClient() != null ? user.getClient().getId() : null
        );
    }
}
