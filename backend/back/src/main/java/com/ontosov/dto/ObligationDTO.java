package com.ontosov.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ObligationDTO {
    private String type;                // "notify" or "compensate"
    private Map<String, String> details = new HashMap<>();
}