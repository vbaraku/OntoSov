package com.ontosov.models;

import com.ontosov.dto.DecisionResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "access_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccessLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long controllerId;
    private Long subjectId;
    private String action;
    private String purpose;
    private String dataDescription;
    private String dataSource;
    private String dataProperty;

    @Enumerated(EnumType.STRING)
    private DecisionResult decision;

    private String reason;
    private String policyGroupId;
    private Integer policyVersion;

    private LocalDateTime requestTime;
    private String blockchainTxHash;

    @Column(name = "blockchain_log_index")
    private Long blockchainLogIndex;
}