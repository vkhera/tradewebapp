package com.example.stockbrokerage.controller;

import com.example.stockbrokerage.entity.Client;
import com.example.stockbrokerage.entity.User;
import com.example.stockbrokerage.repository.UserRepository;
import com.example.stockbrokerage.service.ImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the authorisation logic in {@link ImportController}.
 *
 * Tests that:
 * - ADMIN users can import for any clientId
 * - CLIENT users can only import for their own clientId
 * - Attempts to import for a different client throw 403
 */
class ImportControllerTest {

    private ImportController controller;
    private UserRepository userRepository;
    private ImportService importService;

    private Authentication authentication;
    private SecurityContext securityContext;

    @BeforeEach
    void setUp() {
        importService    = mock(ImportService.class);
        userRepository   = mock(UserRepository.class);
        controller       = new ImportController(importService, userRepository);

        authentication   = mock(Authentication.class);
        securityContext  = mock(SecurityContext.class);

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        SecurityContextHolder.setContext(securityContext);
    }

    // ── Admin access ─────────────────────────────────────────────────────────

    @Test
    void adminUser_canCleanupAnyClient() {
        User admin = adminUser("adminUser");
        when(authentication.getName()).thenReturn("adminUser");
        when(userRepository.findByUsername("adminUser")).thenReturn(Optional.of(admin));

        // Invoking the private checkAuthorized(99L) via the public endpoint
        // We test it indirectly by asserting no exception is thrown.
        // Direct private-method testing via reflection:
        invokeCheckAuthorized(99L); // should not throw
    }

    // ── Client access ─────────────────────────────────────────────────────────

    @Test
    void clientUser_canAccessOwnClientId() {
        User client = clientUser("alice", 5L);
        when(authentication.getName()).thenReturn("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(client));

        invokeCheckAuthorized(5L); // should not throw
    }

    @Test
    void clientUser_cannotAccessOtherClientId() {
        User client = clientUser("bob", 5L);
        when(authentication.getName()).thenReturn("bob");
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(client));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> invokeCheckAuthorized(99L));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void unauthenticated_throws401() {
        when(authentication.isAuthenticated()).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> invokeCheckAuthorized(1L));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unknownUser_throws401() {
        when(authentication.getName()).thenReturn("ghost");
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> invokeCheckAuthorized(1L));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void clientWithoutLinkedClient_throws403() {
        User user = new User();
        user.setRole(User.Role.CLIENT);
        user.setClient(null); // no client linked
        when(authentication.getName()).thenReturn("orphan");
        when(userRepository.findByUsername("orphan")).thenReturn(Optional.of(user));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> invokeCheckAuthorized(1L));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void invokeCheckAuthorized(Long clientId) {
        try {
            var method = ImportController.class.getDeclaredMethod("checkAuthorized", Long.class);
            method.setAccessible(true);
            method.invoke(controller, clientId);
        } catch (java.lang.reflect.InvocationTargetException ite) {
            Throwable cause = ite.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException(cause);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private User adminUser(String username) {
        User u = new User();
        u.setUsername(username);
        u.setRole(User.Role.ADMIN);
        return u;
    }

    private User clientUser(String username, Long clientId) {
        Client c = new Client();
        c.setId(clientId);

        User u = new User();
        u.setUsername(username);
        u.setRole(User.Role.CLIENT);
        u.setClient(c);
        return u;
    }
}
