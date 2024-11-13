package com.ontosov.controllers;

import com.ontosov.models.User;
import com.ontosov.services.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {
    @Autowired
    private AuthService authService;

    @PostMapping("/signup")
    public User signup(@RequestBody User user) {
        return authService.registerUser(user.getTaxid(), user.getEmail(), user.getPassword());
    }

    @PostMapping("/login")
    public User login(@RequestBody User user) {
        User authenticatedUser = authService.authenticateUser(user.getEmail(), user.getPassword());
        if (authenticatedUser != null) {
            return authenticatedUser;
        }
        return null;
    }
}
