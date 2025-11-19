package com.ontosov.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class PolicyGroupDTO {
    private String id;
    private String name;
    private String description;
    private Map<String, Boolean> permissions;
    private Map<String, Object> constraints;
    private Map<String, Object> consequences;
    private Map<String, Object> aiRestrictions;
    private List<String> transformations;  // ODS transformation actions (anonymize, pseudonymize, encrypt)
}
