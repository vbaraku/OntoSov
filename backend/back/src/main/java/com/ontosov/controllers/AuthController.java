package com.ontosov.controllers;

import com.ontosov.dto.LoginRequestDTO;
import com.ontosov.dto.SignupRequestDTO;
import com.ontosov.models.User;
import com.ontosov.models.UserRole;
import com.ontosov.services.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {
    @Autowired
    private AuthService authService;

    @PostMapping("/signup")
    public User signup(@RequestBody SignupRequestDTO request) {
        String taxidOrName = request.getRole() == UserRole.SUBJECT ? request.getTaxid() : request.getName();
        return authService.registerUser(request.getEmail(), request.getPassword(), request.getRole(), taxidOrName);
    }

    @PostMapping("/login")
    public User login(@RequestBody LoginRequestDTO request) {
        return authService.authenticateUser(request.getEmail(), request.getPassword());
    }
}
