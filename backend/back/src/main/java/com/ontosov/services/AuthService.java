package com.ontosov.services;

import com.ontosov.models.User;
import com.ontosov.models.UserRole;
import com.ontosov.repositories.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    @Autowired
    private UserRepo userRepo;
    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public User registerUser(String email, String password, UserRole role, String taxidOrName) {
        String passwordHash = passwordEncoder.encode(password);
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setRole(role);

        if (role == UserRole.SUBJECT) {
            user.setTaxid(taxidOrName);
        } else {
            user.setName(taxidOrName);
        }

        return userRepo.save(user);
    }

    public User authenticateUser(String email, String password) {
        User user = userRepo.findByEmail(email);
        if (user == null || password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        return user;
    }
}
