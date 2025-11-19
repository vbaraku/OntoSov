package com.ontosov.services;

import com.ontosov.dto.PolicyGroupDTO;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PolicyTemplateService {

    public List<PolicyGroupDTO> getPrivacyTierTemplates() {
        List<PolicyGroupDTO> templates = new ArrayList<>();

        // Tier 1: Public Data (Minimal Restrictions)
        templates.add(createPublicDataTemplate());

        // Tier 2: Limited Sharing Data
        templates.add(createLimitedSharingTemplate());

        // Tier 3: Sensitive Data
        templates.add(createSensitiveDataTemplate());

        // Tier 4: Highly Restricted Data
        templates.add(createHighlyRestrictedTemplate());

        return templates;
    }

    private PolicyGroupDTO createPublicDataTemplate() {
        PolicyGroupDTO template = new PolicyGroupDTO();
        template.setName("Tier 1: Public Data");
        template.setDescription("For data you're comfortable sharing widely with minimal restrictions");

        // Permissions
        Map<String, Boolean> permissions = new HashMap<>();
        permissions.put("read", true);
        permissions.put("use", true);
        permissions.put("share", true);
        permissions.put("aggregate", true);
        permissions.put("modify", false);
        template.setPermissions(permissions);

        // Constraints
        Map<String, Object> constraints = new HashMap<>();
        constraints.put("purpose", "");
        constraints.put("expiration", null);
        constraints.put("requiresNotification", false);
        template.setConstraints(constraints);

        // Consequences
        Map<String, Object> consequences = new HashMap<>();
        consequences.put("notificationType", "email");
        consequences.put("compensationAmount", "");
        template.setConsequences(consequences);

        // AI Restrictions
        Map<String, Object> aiRestrictions = new HashMap<>();
        aiRestrictions.put("allowAiTraining", true);
        aiRestrictions.put("aiAlgorithm", "");
        template.setAiRestrictions(aiRestrictions);

        // Transformations
        template.setTransformations(new ArrayList<>());

        return template;
    }

    private PolicyGroupDTO createLimitedSharingTemplate() {
        PolicyGroupDTO template = new PolicyGroupDTO();
        template.setName("Tier 2: Limited Sharing");
        template.setDescription("For data that can be used but with limited sharing");

        // Permissions
        Map<String, Boolean> permissions = new HashMap<>();
        permissions.put("read", true);
        permissions.put("use", true);
        permissions.put("share", false);
        permissions.put("aggregate", true);
        permissions.put("modify", false);
        template.setPermissions(permissions);

        // Constraints
        Map<String, Object> constraints = new HashMap<>();
        constraints.put("purpose", "Service Provision");
        constraints.put("expiration", null);
        constraints.put("requiresNotification", true);
        template.setConstraints(constraints);

        // Consequences
        Map<String, Object> consequences = new HashMap<>();
        consequences.put("notificationType", "email");
        consequences.put("compensationAmount", "");
        template.setConsequences(consequences);

        // AI Restrictions
        Map<String, Object> aiRestrictions = new HashMap<>();
        aiRestrictions.put("allowAiTraining", true);
        aiRestrictions.put("aiAlgorithm", "");
        template.setAiRestrictions(aiRestrictions);

        // Transformations
        template.setTransformations(new ArrayList<>());

        return template;
    }

    private PolicyGroupDTO createSensitiveDataTemplate() {
        PolicyGroupDTO template = new PolicyGroupDTO();
        template.setName("Tier 3: Sensitive Data");
        template.setDescription("For sensitive data that requires careful handling");

        // Permissions
        Map<String, Boolean> permissions = new HashMap<>();
        permissions.put("read", true);
        permissions.put("use", true);
        permissions.put("share", false);
        permissions.put("aggregate", false);
        permissions.put("modify", false);
        template.setPermissions(permissions);

        // Constraints
        Map<String, Object> constraints = new HashMap<>();
        constraints.put("purpose", "Service Provision");  // Updated to match DPV taxonomy
        constraints.put("expiration", null);
        constraints.put("requiresNotification", true);
        template.setConstraints(constraints);

        // Consequences
        Map<String, Object> consequences = new HashMap<>();
        consequences.put("notificationType", "email");
        consequences.put("compensationAmount", "100");
        template.setConsequences(consequences);

        // AI Restrictions
        Map<String, Object> aiRestrictions = new HashMap<>();
        aiRestrictions.put("allowAiTraining", true);
        aiRestrictions.put("aiAlgorithm", "federatedLearning");
        template.setAiRestrictions(aiRestrictions);

        // Transformations
        template.setTransformations(new ArrayList<>());

        return template;
    }

    private PolicyGroupDTO createHighlyRestrictedTemplate() {
        PolicyGroupDTO template = new PolicyGroupDTO();
        template.setName("Tier 4: Highly Restricted");
        template.setDescription("For your most private data with strict usage limits");

        // Permissions
        Map<String, Boolean> permissions = new HashMap<>();
        permissions.put("read", true);
        permissions.put("use", false);
        permissions.put("share", false);
        permissions.put("aggregate", false);
        permissions.put("modify", false);
        template.setPermissions(permissions);

        // Constraints
        Map<String, Object> constraints = new HashMap<>();
        constraints.put("purpose", "Service Provision");  // Updated to match DPV taxonomy
        constraints.put("expiration", null);
        constraints.put("requiresNotification", true);
        template.setConstraints(constraints);

        // Consequences
        Map<String, Object> consequences = new HashMap<>();
        consequences.put("notificationType", "email");
        consequences.put("compensationAmount", "500");
        template.setConsequences(consequences);

        // AI Restrictions
        Map<String, Object> aiRestrictions = new HashMap<>();
        aiRestrictions.put("allowAiTraining", false);
        aiRestrictions.put("aiAlgorithm", "");
        template.setAiRestrictions(aiRestrictions);

        // Transformations
        template.setTransformations(new ArrayList<>());

        return template;
    }
}