package com.ontosov.controllers;

import com.ontosov.dto.PolicyAssignmentDTO;
import com.ontosov.dto.PolicyGroupDTO;
import com.ontosov.dto.PolicyStatusDTO;

import com.ontosov.services.ODRLService;
import com.ontosov.services.PolicyGroupService;
import com.ontosov.services.PolicyTemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/policy-groups")
public class PolicyGroupController {
    private static final Logger log = LoggerFactory.getLogger(ODRLService.class);

    @Autowired
    private PolicyGroupService policyGroupService;

    @Autowired
    private ODRLService odrlService;

    @Autowired
    private PolicyTemplateService policyTemplateService;

    @GetMapping("/{subjectId}")
    public ResponseEntity<List<PolicyGroupDTO>> getPolicyGroups(@PathVariable Long subjectId) {
        try {
            List<PolicyGroupDTO> groups = policyGroupService.getPolicyGroupsBySubject(subjectId);
            return ResponseEntity.ok(groups);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/templates")
    public ResponseEntity<List<PolicyGroupDTO>> getPolicyTemplates() {
        try {
            List<PolicyGroupDTO> templates = policyTemplateService.getPrivacyTierTemplates();
            return ResponseEntity.ok(templates);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping
    public ResponseEntity<String> createPolicyGroup(
            @RequestBody PolicyGroupDTO policyGroupDTO,
            @RequestParam Long subjectId) {
        try {
            String groupId = policyGroupService.createPolicyGroup(policyGroupDTO, subjectId);
            return ResponseEntity.ok(groupId);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping("/{groupId}")
    public ResponseEntity<Void> updatePolicyGroup(
            @PathVariable String groupId,
            @RequestBody PolicyGroupDTO policyGroupDTO,
            @RequestParam Long subjectId) {
        try {
            policyGroupService.updatePolicyGroup(groupId, policyGroupDTO, subjectId);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/{groupId}")
    public ResponseEntity<Void> deletePolicyGroup(
            @PathVariable String groupId,
            @RequestParam Long subjectId) {
        try {
            policyGroupService.deletePolicyGroup(groupId, subjectId);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{groupId}/assign")
    public ResponseEntity<Void> assignDataToPolicy(
            @PathVariable String groupId,
            @RequestBody PolicyAssignmentDTO assignmentDTO,
            @RequestParam Long subjectId) {

        log.info("=== ASSIGNMENT REQUEST RECEIVED ===");
        log.info("Group ID: {}", groupId);
        log.info("Subject ID: {}", subjectId);
        log.info("Assignment DTO: {}", assignmentDTO);

        try {
            // Get the policy group details first (outside transaction)
            List<PolicyGroupDTO> groups = policyGroupService.getPolicyGroupsBySubject(subjectId);
            PolicyGroupDTO policyGroup = groups.stream()
                    .filter(g -> g.getId().equals(groupId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Policy group not found"));

            // Do everything in one transaction
            policyGroupService.assignDataToPolicy(groupId, assignmentDTO, policyGroup, subjectId);

            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            log.error("Bad request in policy assignment: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Internal error in policy assignment: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Bulk assign policy to all unprotected data for a subject
     * The frontend sends the list of unprotected items since it already has this info
     */
    @PostMapping("/{groupId}/assign-all-unprotected")
    public ResponseEntity<?> assignAllUnprotectedData(
            @PathVariable String groupId,
            @RequestBody PolicyAssignmentDTO assignmentDTO,
            @RequestParam Long subjectId) {

        log.info("=== BULK ASSIGNMENT REQUEST RECEIVED ===");
        log.info("Group ID: {}", groupId);
        log.info("Subject ID: {}", subjectId);
        log.info("Assignment DTO: {}", assignmentDTO);

        try {
            // Get the policy group details
            List<PolicyGroupDTO> groups = policyGroupService.getPolicyGroupsBySubject(subjectId);
            PolicyGroupDTO policyGroup = groups.stream()
                    .filter(g -> g.getId().equals(groupId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Policy group not found"));

            // Use existing assignment method
            policyGroupService.assignDataToPolicy(groupId, assignmentDTO, policyGroup, subjectId);

            // Calculate totals for response
            int propertiesCount = assignmentDTO.getPropertyAssignments() != null
                    ? assignmentDTO.getPropertyAssignments().values().stream().mapToInt(Set::size).sum()
                    : 0;
            int entitiesCount = assignmentDTO.getEntityAssignments() != null
                    ? assignmentDTO.getEntityAssignments().values().stream().mapToInt(Set::size).sum()
                    : 0;
            int totalAssigned = propertiesCount + entitiesCount;

            log.info("Successfully assigned {} items", totalAssigned);

            return ResponseEntity.ok(Map.of(
                    "message", "Successfully protected " + totalAssigned + " unprotected data elements",
                    "propertiesProtected", propertiesCount,
                    "entitiesProtected", entitiesCount,
                    "totalProtected", totalAssigned
            ));

        } catch (IllegalArgumentException e) {
            log.error("Bad request in bulk assignment: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Internal error in bulk assignment: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{groupId}/assignments")
    public ResponseEntity<Map<String, Object>> getPolicyGroupAssignments(
            @PathVariable String groupId,
            @RequestParam Long subjectId) {
        try {
            Map<String, Object> assignments = odrlService.getAssignmentsForPolicyGroup(groupId, subjectId);
            return ResponseEntity.ok(assignments);
        } catch (Exception e) {
            log.error("Error fetching assignments for group {}: {}", groupId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/status/{subjectId}")
    public ResponseEntity<PolicyStatusDTO> getPolicyStatus(@PathVariable Long subjectId) {
        try {
            PolicyStatusDTO status = new PolicyStatusDTO();
            status.setAssignedPolicies(odrlService.getSubjectPolicies(subjectId));
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}