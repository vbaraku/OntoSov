package com.ontosov.constants;

import java.util.HashMap;
import java.util.Map;

/**
 * Data Privacy Vocabulary (DPV) Purpose Taxonomy
 * Based on W3C DPV specification: https://w3c.github.io/dpv/
 */
public class DPVPurpose {
    private static final String DPV_NS = "https://w3id.org/dpv#";

    // DPV Purpose URIs
    public static final String SERVICE_PROVISION = DPV_NS + "ServiceProvision";
    public static final String MEDICAL_HEALTH = DPV_NS + "MedicalHealth";
    public static final String MARKETING = DPV_NS + "Marketing";
    public static final String PERSONALISED_ADVERTISING = DPV_NS + "PersonalisedAdvertising";
    public static final String RESEARCH_AND_DEVELOPMENT = DPV_NS + "ResearchAndDevelopment";
    public static final String ACADEMIC_RESEARCH = DPV_NS + "AcademicResearch";
    public static final String COMMERCIAL_RESEARCH = DPV_NS + "CommercialResearch";
    public static final String PERSONALIZATION = DPV_NS + "Personalization";
    public static final String FRAUD_PREVENTION_AND_DETECTION = DPV_NS + "FraudPreventionAndDetection";
    public static final String COMMUNICATION_MANAGEMENT = DPV_NS + "CommunicationManagement";

    // Human-readable labels for UI
    private static final Map<String, String> PURPOSE_LABELS = new HashMap<>();

    static {
        PURPOSE_LABELS.put(SERVICE_PROVISION, "Service Provision");
        PURPOSE_LABELS.put(MEDICAL_HEALTH, "Medical Treatment");
        PURPOSE_LABELS.put(MARKETING, "Marketing");
        PURPOSE_LABELS.put(PERSONALISED_ADVERTISING, "Personalized Advertising");
        PURPOSE_LABELS.put(RESEARCH_AND_DEVELOPMENT, "Research and Development");
        PURPOSE_LABELS.put(ACADEMIC_RESEARCH, "Academic Research");
        PURPOSE_LABELS.put(COMMERCIAL_RESEARCH, "Commercial Research");
        PURPOSE_LABELS.put(PERSONALIZATION, "Personalization");
        PURPOSE_LABELS.put(FRAUD_PREVENTION_AND_DETECTION, "Fraud Prevention");
        PURPOSE_LABELS.put(COMMUNICATION_MANAGEMENT, "Communication Management");
    }

    /**
     * Get human-readable label for a DPV purpose URI
     */
    public static String getLabel(String purposeUri) {
        return PURPOSE_LABELS.getOrDefault(purposeUri, purposeUri);
    }

    /**
     * Get DPV purpose URI from human-readable label
     */
    public static String getUri(String label) {
        for (Map.Entry<String, String> entry : PURPOSE_LABELS.entrySet()) {
            if (entry.getValue().equals(label)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Get all available purpose labels
     */
    public static Map<String, String> getAllPurposes() {
        return new HashMap<>(PURPOSE_LABELS);
    }

    /**
     * Check if a given string is a valid DPV purpose URI
     */
    public static boolean isValidPurposeUri(String uri) {
        return PURPOSE_LABELS.containsKey(uri);
    }

    /**
     * Check if a given string is a valid DPV purpose label
     */
    public static boolean isValidPurposeLabel(String label) {
        return PURPOSE_LABELS.containsValue(label);
    }
}
