import React, { useState } from "react";
import {
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    Button,
    FormControl,
    InputLabel,
    Select,
    MenuItem,
    Typography,
    Alert,
    Box,
    CircularProgress,
} from "@mui/material";
import { Lock as LockIcon } from "@mui/icons-material";

const ProtectAllDataDialog = ({
    open,
    onClose,
    data,
    policyGroups,
    user,
    isPropertyProtected,
    isEntityProtected,
    onSuccess,
}) => {
    const [selectedPolicyForBulk, setSelectedPolicyForBulk] = useState(null);
    const [bulkAssigning, setBulkAssigning] = useState(false);

    // Calculate unprotected count
    const calculateUnprotectedCount = () => {
        let count = 0;

        Object.entries(data).forEach(([source, sourceData]) => {
            // Check Person properties
            if (sourceData.Person) {
                Object.keys(sourceData.Person).forEach((property) => {
                    if (!isPropertyProtected(source, property)) {
                        count++;
                    }
                });
            }

            // Check entity data
            Object.entries(sourceData).forEach(([entityType, entityArray]) => {
                if (entityType === "Person" || !Array.isArray(entityArray)) return;

                entityArray.forEach((entity) => {
                    if (!isEntityProtected(source, entityType, entity.entityId)) {
                        count++;
                    }
                });
            });
        });

        return count;
    };

    const handleProtectAllConfirm = async () => {
        if (!selectedPolicyForBulk) {
            return;
        }

        setBulkAssigning(true);

        try {
            // Extract the real ID from the policy URI format
            const realGroupId = selectedPolicyForBulk.id.includes("#")
                ? selectedPolicyForBulk.id.split("#")[1]
                : selectedPolicyForBulk.id;

            // STEP 1: Fetch existing assignments for this policy group
            console.log("Fetching existing assignments for policy group:", realGroupId);
            const existingResponse = await fetch(
                `http://localhost:8080/api/policy-groups/${realGroupId}/assignments?subjectId=${user.id}`
            );

            let existingAssignments = {
                properties: {},
                entities: {},
            };

            if (existingResponse.ok) {
                const existing = await existingResponse.json();
                console.log("Existing assignments:", existing);

                // Convert existing assignments to our format
                if (existing.propertyAssignments) {
                    Object.entries(existing.propertyAssignments).forEach(([source, props]) => {
                        existingAssignments.properties[source] = Array.isArray(props) ? props : [];
                    });
                }

                if (existing.entityAssignments) {
                    Object.entries(existing.entityAssignments).forEach(([source, entities]) => {
                        existingAssignments.entities[source] = Array.isArray(entities) ? entities : [];
                    });
                }
            }

            // STEP 2: Collect all unprotected data
            const unprotectedData = {
                properties: {},
                entities: {},
            };

            Object.entries(data).forEach(([source, sourceData]) => {
                // Check Person properties
                if (sourceData.Person) {
                    Object.keys(sourceData.Person).forEach((property) => {
                        if (!isPropertyProtected(source, property)) {
                            if (!unprotectedData.properties[source]) {
                                unprotectedData.properties[source] = [];
                            }
                            unprotectedData.properties[source].push(property);
                        }
                    });
                }

                // Check entity data
                Object.entries(sourceData).forEach(([entityType, entityArray]) => {
                    if (entityType === "Person" || !Array.isArray(entityArray)) return;

                    entityArray.forEach((entity) => {
                        if (!isEntityProtected(source, entityType, entity.entityId)) {
                            if (!unprotectedData.entities[source]) {
                                unprotectedData.entities[source] = [];
                            }
                            unprotectedData.entities[source].push(entity.entityId);
                        }
                    });
                });
            });

            console.log("Unprotected data found:", unprotectedData);

            // Check if there's anything to protect
            const totalUnprotected =
                Object.values(unprotectedData.properties).flat().length +
                Object.values(unprotectedData.entities).flat().length;

            if (totalUnprotected === 0) {
                onSuccess("No unprotected data found", "info");
                handleClose();
                return;
            }

            // STEP 3: MERGE existing + unprotected (this is the fix!)
            const mergedAssignments = {
                properties: { ...existingAssignments.properties },
                entities: { ...existingAssignments.entities },
            };

            // Merge properties
            Object.entries(unprotectedData.properties).forEach(([source, props]) => {
                if (!mergedAssignments.properties[source]) {
                    mergedAssignments.properties[source] = [];
                }
                // Add new properties, avoid duplicates
                props.forEach((prop) => {
                    if (!mergedAssignments.properties[source].includes(prop)) {
                        mergedAssignments.properties[source].push(prop);
                    }
                });
            });

            // Merge entities
            Object.entries(unprotectedData.entities).forEach(([source, entities]) => {
                if (!mergedAssignments.entities[source]) {
                    mergedAssignments.entities[source] = [];
                }
                // Add new entities, avoid duplicates
                entities.forEach((entity) => {
                    if (!mergedAssignments.entities[source].includes(entity)) {
                        mergedAssignments.entities[source].push(entity);
                    }
                });
            });

            console.log("Merged assignments (existing + new):", mergedAssignments);

            // STEP 4: Send merged assignments to backend
            const response = await fetch(
                `http://localhost:8080/api/policy-groups/${realGroupId}/assign-all-unprotected?subjectId=${user.id}`,
                {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json",
                    },
                    body: JSON.stringify({
                        propertyAssignments: mergedAssignments.properties,
                        entityAssignments: mergedAssignments.entities,
                    }),
                }
            );

            if (!response.ok) {
                const errorData = await response.json().catch(() => ({}));
                throw new Error(errorData.error || "Failed to protect all data");
            }

            const result = await response.json();
            console.log("Backend response:", result);

            // Show success message with actual count of NEW items protected
            onSuccess(
                `Successfully protected ${totalUnprotected} new item${totalUnprotected !== 1 ? 's' : ''}`,
                "success"
            );
            handleClose();
        } catch (err) {
            console.error("Error protecting all data:", err);
            onSuccess(`Failed to protect data: ${err.message}`, "error");
        } finally {
            setBulkAssigning(false);
        }
    };

    const handleClose = () => {
        if (!bulkAssigning) {
            setSelectedPolicyForBulk(null);
            onClose();
        }
    };

    const unprotectedCount = calculateUnprotectedCount();

    return (
        <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
            <DialogTitle>
                <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                    <LockIcon color="primary" />
                    <Typography variant="h6">Protect All Unprotected Data</Typography>
                </Box>
            </DialogTitle>
            <DialogContent>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                    This will apply a policy group to all data elements that currently
                    have no protection. Existing protections will be preserved.
                </Typography>

                <Alert severity="info" sx={{ mb: 3 }}>
                    <strong>{unprotectedCount}</strong> unprotected item
                    {unprotectedCount !== 1 ? "s" : ""} found
                </Alert>

                {policyGroups.length === 0 && (
                    <Alert severity="warning" sx={{ mb: 3 }}>
                        No policy groups available. Please create a policy group first.
                    </Alert>
                )}

                <FormControl fullWidth disabled={policyGroups.length === 0}>
                    <InputLabel>Select Policy Group</InputLabel>
                    <Select
                        value={selectedPolicyForBulk?.id || ""}
                        onChange={(e) => {
                            const selected = policyGroups.find(
                                (pg) => pg.id === e.target.value
                            );
                            setSelectedPolicyForBulk(selected);
                        }}
                        label="Select Policy Group"
                        disabled={bulkAssigning || policyGroups.length === 0}
                    >
                        {policyGroups.map((group) => (
                            <MenuItem key={group.id} value={group.id}>
                                <Box>
                                    <Typography variant="body1">{group.name}</Typography>
                                    <Typography variant="caption" color="text.secondary">
                                        {group.description}
                                    </Typography>
                                </Box>
                            </MenuItem>
                        ))}
                    </Select>
                </FormControl>

                {selectedPolicyForBulk && (
                    <Box sx={{ mt: 2, p: 2, bgcolor: "grey.50", borderRadius: 1 }}>
                        <Typography variant="subtitle2" gutterBottom>
                            Policy Summary:
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                            • Read: {selectedPolicyForBulk.permissions?.read ? "✓" : "✗"}
                            <br />
                            • Use: {selectedPolicyForBulk.permissions?.use ? "✓" : "✗"}
                            <br />
                            • Share: {selectedPolicyForBulk.permissions?.share ? "✓" : "✗"}
                            <br />• AI Training:{" "}
                            {selectedPolicyForBulk.aiRestrictions?.allowAiTraining
                                ? "✓"
                                : "✗"}
                        </Typography>
                    </Box>
                )}
            </DialogContent>
            <DialogActions>
                <Button onClick={handleClose} disabled={bulkAssigning}>
                    Cancel
                </Button>
                <Button
                    onClick={handleProtectAllConfirm}
                    variant="contained"
                    disabled={!selectedPolicyForBulk || bulkAssigning || unprotectedCount === 0}
                    startIcon={
                        bulkAssigning ? <CircularProgress size={20} /> : <LockIcon />
                    }
                >
                    {bulkAssigning ? "Protecting..." : "Protect All"}
                </Button>
            </DialogActions>
        </Dialog>
    );
};

export default ProtectAllDataDialog;