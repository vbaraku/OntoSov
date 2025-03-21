package com.ontosov.dto;

import lombok.Data;
import java.util.Map;
import java.util.Set;

@Data
public class PolicyStatusDTO {
    private Map<String, Map<String, Map<String, Set<String>>>> assignedPolicies; // GroupId -> (Source -> (Property -> Actions))
}

