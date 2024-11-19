package com.ontosov.dto;

import com.ontosov.models.UserRole;
import lombok.Data;

@Data
public class SignupRequestDTO {
    private String email;
    private String password;
    private UserRole role;
    private String taxid;
    private String name;
}