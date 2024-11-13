package com.ontosov.services;

import com.ontosov.models.User;
import com.ontosov.repositories.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    @Autowired
    private UserRepo userRepo;

    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public User registerUser(String taxid, String email, String password) {
        String passwordHash = passwordEncoder.encode(password);
        User user = new User();
        user.setTaxid(taxid);
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        return userRepo.save(user);
    }

    public User authenticateUser(String email, String password) {
        User user = userRepo.findByEmail(email);
        if (user == null) {
            throw new IllegalArgumentException("User not found with email: " + email);
        }
        if (password == null) {
            throw new IllegalArgumentException("Password cannot be null");
        }
        boolean passwordMatch = passwordEncoder.matches(password, user.getPasswordHash());
        if (!passwordMatch) {
            throw new IllegalArgumentException("Incorrect password");
        }

        return user;
    }

}
