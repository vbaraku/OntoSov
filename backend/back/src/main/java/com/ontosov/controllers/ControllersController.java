package com.ontosov.controllers;

import com.ontosov.models.User;
import com.ontosov.models.UserRole;
import com.ontosov.repositories.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ControllersController {

    @Autowired
    private UserRepo userRepo;

    @GetMapping("/controllers")
    public List<User> getAllControllers() {
        return userRepo.findByRole(UserRole.CONTROLLER);
    }
}