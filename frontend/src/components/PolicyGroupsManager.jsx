import React, { useState, useEffect } from "react";
import {
  Box,
  Typography,
  FormControl,
  FormControlLabel,
  Checkbox,
  TextField,
  Button,
  Tooltip,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  List,
  ListItem,
  ListItemText,
  ListItemSecondaryAction,
  Card,
  CardContent,
  CardActions,
  CircularProgress,
  Alert,
  Snackbar,
  Grid,
  InputLabel,
  Select,
  MenuItem,
  FormHelperText,
  Chip,
} from "@mui/material";
import { DatePicker } from "@mui/x-date-pickers/DatePicker";
import HelpOutlineIcon from "@mui/icons-material/HelpOutline";
import EditIcon from "@mui/icons-material/Edit";
import DeleteIcon from "@mui/icons-material/Delete";
import AddIcon from "@mui/icons-material/Add";
import LayersIcon from "@mui/icons-material/Layers";
import PolicyIcon from "@mui/icons-material/Policy";

// DPV Purpose Taxonomy Options
const DPV_PURPOSES = [
  { value: "Service Provision", label: "Service Provision" },
  { value: "Medical Treatment", label: "Medical Treatment" },
  { value: "Marketing", label: "Marketing" },
  { value: "Personalized Advertising", label: "Personalized Advertising" },
  { value: "Research and Development", label: "Research and Development" },
  { value: "Academic Research", label: "Academic Research" },
  { value: "Commercial Research", label: "Commercial Research" },
  { value: "Personalization", label: "Personalization" },
  { value: "Fraud Prevention", label: "Fraud Prevention" },
  { value: "Communication Management", label: "Communication Management" },
];

const tooltips = {
  read: "Basic access to view the data (e.g., viewing your profile information)",
  use: "Using data for operations (e.g., processing transactions, providing services)",
  share:
    "Sharing data with authorized third parties (e.g., sharing medical data with specialists)",
  aggregate: "Using data for anonymous statistics and analytics",
  modify: "Making updates or changes to the data",
  notification:
    "Receive notifications when your data is accessed according to allowed permissions",
  allowAiTraining: "Allow your data to be used for training AI models",
  aiAlgorithm: "Specify which AI algorithm can be used to process your data",
};

const initialFormState = {
  name: "",
  description: "",
  permissions: {
    read: false,
    use: false,
    share: false,
    aggregate: false,
    modify: false,
  },
  constraints: {
    purpose: "",
    expiration: null,
    requiresNotification: false,
  },
  consequences: {
    notificationType: "email",
    compensationAmount: "",
  },
  aiRestrictions: {
    allowAiTraining: true,
    aiAlgorithm: "",
  },
  transformations: [],
};

const PolicyGroupForm = React.memo(
  ({ formState, setFormState, onSave, onCancel }) => {
    const renderPermissionWithTooltip = (permission, label) => (
      <Box sx={{ display: "flex", alignItems: "center", mb: 1 }}>
        <FormControlLabel
          control={
            <Checkbox
              checked={formState.permissions[permission]}
              onChange={(e) =>
                setFormState((prev) => ({
                  ...prev,
                  permissions: {
                    ...prev.permissions,
                    [permission]: e.target.checked,
                  },
                }))
              }
            />
          }
          label={`Allow ${label}`}
        />
        <Tooltip title={tooltips[permission]} arrow>
          <IconButton size="small">
            <HelpOutlineIcon fontSize="small" />
          </IconButton>
        </Tooltip>
      </Box>
    );

    return (
      <Box sx={{ p: 2 }}>
        <TextField
          fullWidth
          label="Group Name"
          value={formState.name}
          onChange={(e) =>
            setFormState((prev) => ({ ...prev, name: e.target.value }))
          }
          sx={{ mb: 2 }}
        />

        <TextField
          fullWidth
          label="Description"
          value={formState.description}
          onChange={(e) =>
            setFormState((prev) => ({ ...prev, description: e.target.value }))
          }
          multiline
          rows={2}
          sx={{ mb: 3 }}
        />

        <Typography variant="h6" gutterBottom>
          Permissions
        </Typography>
        {renderPermissionWithTooltip("read", "Read")}
        {renderPermissionWithTooltip("use", "Use")}
        {renderPermissionWithTooltip("share", "Share")}
        {renderPermissionWithTooltip("aggregate", "Aggregate")}
        {renderPermissionWithTooltip("modify", "Modify")}

        <Typography variant="h6" gutterBottom sx={{ mt: 3 }}>
          Constraints
        </Typography>
        <FormControl fullWidth sx={{ mb: 2 }}>
          <InputLabel>Purpose</InputLabel>
          <Select
            value={formState.constraints.purpose}
            label="Purpose"
            onChange={(e) =>
              setFormState((prev) => ({
                ...prev,
                constraints: {
                  ...prev.constraints,
                  purpose: e.target.value,
                },
              }))
            }
          >
            <MenuItem value="">
              <em>None</em>
            </MenuItem>
            {DPV_PURPOSES.map((purpose) => (
              <MenuItem key={purpose.value} value={purpose.value}>
                {purpose.label}
              </MenuItem>
            ))}
          </Select>
          <FormHelperText>
            Select the purpose for data processing
          </FormHelperText>
        </FormControl>

        <FormControl fullWidth sx={{ mb: 3 }}>
          <DatePicker
            label="Expiration Date"
            value={formState.constraints.expiration}
            onChange={(date) =>
              setFormState((prev) => ({
                ...prev,
                constraints: {
                  ...prev.constraints,
                  expiration: date,
                },
              }))
            }
            slotProps={{
              textField: {
                helperText: "Leave empty for no expiration",
              },
            }}
          />
        </FormControl>

        <Box sx={{ mb: 2 }}>
          <Box sx={{ display: "flex", alignItems: "center", mb: 1 }}>
            <Typography variant="subtitle2">Data Transformations</Typography>
            <Tooltip title="Require data to be transformed (anonymized, pseudonymized, or encrypted) before the controller can use it" arrow>
              <HelpOutlineIcon sx={{ fontSize: 18, ml: 1, color: "text.secondary" }} />
            </Tooltip>
          </Box>
          <Box sx={{ display: "flex", gap: 2, flexWrap: "wrap" }}>
            <FormControlLabel
              control={
                <Checkbox
                  checked={formState.transformations?.includes("anonymize")}
                  onChange={(e) =>
                    setFormState((prev) => ({
                      ...prev,
                      transformations: e.target.checked
                        ? [...(prev.transformations || []), "anonymize"]
                        : (prev.transformations || []).filter(
                            (t) => t !== "anonymize"
                          ),
                    }))
                  }
                />
              }
              label="Anonymize"
            />
            <FormControlLabel
              control={
                <Checkbox
                  checked={formState.transformations?.includes("pseudonymize")}
                  onChange={(e) =>
                    setFormState((prev) => ({
                      ...prev,
                      transformations: e.target.checked
                        ? [...(prev.transformations || []), "pseudonymize"]
                        : (prev.transformations || []).filter(
                            (t) => t !== "pseudonymize"
                          ),
                    }))
                  }
                />
              }
              label="Pseudonymize"
            />
            <FormControlLabel
              control={
                <Checkbox
                  checked={formState.transformations?.includes("encrypt")}
                  onChange={(e) =>
                    setFormState((prev) => ({
                      ...prev,
                      transformations: e.target.checked
                        ? [...(prev.transformations || []), "encrypt"]
                        : (prev.transformations || []).filter(
                            (t) => t !== "encrypt"
                          ),
                    }))
                  }
                />
              }
              label="Encrypt"
            />
          </Box>
        </Box>

        <Typography variant="h6" gutterBottom>
          Consequences
        </Typography>
        <FormControl fullWidth sx={{ mb: 2 }}>
          <InputLabel>Notification Type</InputLabel>
          <Select
            value={formState.consequences.notificationType}
            label="Notification Type"
            onChange={(e) =>
              setFormState((prev) => ({
                ...prev,
                consequences: {
                  ...prev.consequences,
                  notificationType: e.target.value,
                },
              }))
            }
          >
            <MenuItem value="email">Email</MenuItem>
          </Select>
          <FormHelperText>
            How you'll be notified of policy violations
          </FormHelperText>
        </FormControl>

        <FormControl fullWidth sx={{ mb: 3 }}>
          <TextField
            label="Compensation Amount"
            type="number"
            value={formState.consequences.compensationAmount}
            onChange={(e) =>
              setFormState((prev) => ({
                ...prev,
                consequences: {
                  ...prev.consequences,
                  compensationAmount: e.target.value,
                },
              }))
            }
            placeholder="e.g., 100"
            InputProps={{ startAdornment: "€" }}
            helperText="Monetary compensation in case of data misuse (optional)"
          />
        </FormControl>

        <Typography variant="h6" gutterBottom>
          AI Training Restrictions
        </Typography>
        <Box sx={{ display: "flex", alignItems: "center", mb: 2 }}>
          <FormControlLabel
            control={
              <Checkbox
                checked={formState.aiRestrictions.allowAiTraining}
                onChange={(e) =>
                  setFormState((prev) => ({
                    ...prev,
                    aiRestrictions: {
                      ...prev.aiRestrictions,
                      allowAiTraining: e.target.checked,
                    },
                  }))
                }
              />
            }
            label="Allow AI training on this data"
          />
          <Tooltip title={tooltips.allowAiTraining} arrow>
            <IconButton size="small">
              <HelpOutlineIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        </Box>

        {formState.aiRestrictions.allowAiTraining && (
          <FormControl fullWidth sx={{ mb: 3 }}>
            <InputLabel>Allowed AI Algorithm</InputLabel>
            <Select
              value={formState.aiRestrictions.aiAlgorithm}
              label="Allowed AI Algorithm"
              onChange={(e) =>
                setFormState((prev) => ({
                  ...prev,
                  aiRestrictions: {
                    ...prev.aiRestrictions,
                    aiAlgorithm: e.target.value,
                  },
                }))
              }
            >
              <MenuItem value="">
                <em>Any algorithm</em>
              </MenuItem>
              <MenuItem value="federatedLearning">
                Federated Learning Only
              </MenuItem>
              <MenuItem value="differentialPrivacy">
                Differential Privacy
              </MenuItem>
              <MenuItem value="secureEnclave">
                Secure Enclave Processing
              </MenuItem>
              <MenuItem value="localProcessing">Local Processing Only</MenuItem>
            </Select>
            <FormHelperText>
              Restrict which AI training algorithms can be used
            </FormHelperText>
          </FormControl>
        )}

        <Box
          sx={{ mt: 3, display: "flex", justifyContent: "flex-end", gap: 2 }}
        >
          <Button onClick={onCancel}>Cancel</Button>
          <Button variant="contained" onClick={onSave}>
            {formState.id ? "Save Changes" : "Create Policy"}
          </Button>
        </Box>
      </Box>
    );
  }
);

const AssignPolicyForm = React.memo(
  ({ data, onSave, onCancel, groupName, initialSelectedData = {} }) => {
    console.log(
      "AssignPolicyForm render - initialSelectedData:",
      initialSelectedData
    );

    const [selectedPropertyData, setSelectedPropertyData] = useState({});
    const [selectedEntityData, setSelectedEntityData] = useState({});
    const [initialized, setInitialized] = useState(false);

    // Use a more controlled approach to avoid infinite re-renders
    useEffect(() => {
      console.log("useEffect triggered. Initialized:", initialized);
      console.log("initialSelectedData:", initialSelectedData);

      // Only initialize once or when we get actual data
      if (
        !initialized ||
        (initialSelectedData && Object.keys(initialSelectedData).length > 0)
      ) {
        console.log("Initializing form data...");

        // Handle properties
        const propertyData = initialSelectedData.properties || {};
        const normalizedPropertyData = {};

        Object.entries(propertyData).forEach(([source, items]) => {
          if (items instanceof Set) {
            normalizedPropertyData[source] = items;
          } else if (Array.isArray(items)) {
            normalizedPropertyData[source] = new Set(items);
          } else if (items && typeof items === "object") {
            const selectedProps = Object.keys(items).filter(
              (key) => items[key]
            );
            if (selectedProps.length > 0) {
              normalizedPropertyData[source] = new Set(selectedProps);
            }
          }
        });

        // Handle entities
        const entityData = initialSelectedData.entities || {};
        const normalizedEntityData = {};

        Object.entries(entityData).forEach(([source, items]) => {
          if (items instanceof Set) {
            normalizedEntityData[source] = items;
          } else if (Array.isArray(items)) {
            normalizedEntityData[source] = new Set(items);
          } else if (items && typeof items === "object") {
            const selectedEntities = Object.keys(items).filter(
              (key) => items[key]
            );
            if (selectedEntities.length > 0) {
              normalizedEntityData[source] = new Set(selectedEntities);
            }
          }
        });

        console.log(
          "Setting normalized property data:",
          normalizedPropertyData
        );
        console.log("Setting normalized entity data:", normalizedEntityData);

        setSelectedPropertyData(normalizedPropertyData);
        setSelectedEntityData(normalizedEntityData);
        setInitialized(true);
      }
    }, [JSON.stringify(initialSelectedData), initialized]); // Use JSON.stringify to compare object content

    const togglePropertySelection = (source, property) => {
      setSelectedPropertyData((prev) => {
        const newState = { ...prev };

        if (!newState[source]) {
          newState[source] = new Set([property]);
        } else {
          const newSet = new Set(newState[source]);
          if (newSet.has(property)) {
            newSet.delete(property);
            if (newSet.size === 0) {
              delete newState[source];
            } else {
              newState[source] = newSet;
            }
          } else {
            newSet.add(property);
            newState[source] = newSet;
          }
        }

        return newState;
      });
    };

    const toggleEntitySelection = (source, entityId) => {
      setSelectedEntityData((prev) => {
        const newState = { ...prev };

        if (!newState[source]) {
          newState[source] = new Set([entityId]);
        } else {
          const newSet = new Set(newState[source]);
          if (newSet.has(entityId)) {
            newSet.delete(entityId);
            if (newSet.size === 0) {
              delete newState[source];
            } else {
              newState[source] = newSet;
            }
          } else {
            newSet.add(entityId);
            newState[source] = newSet;
          }
        }

        return newState;
      });
    };

    const isPropertySelected = (source, property) => {
      return selectedPropertyData[source]?.has(property) || false;
    };

    const isEntitySelected = (source, entityId) => {
      return selectedEntityData[source]?.has(entityId) || false;
    };

    const handleSave = () => {
      // Convert Sets back to Arrays for sending to backend
      const propertyAssignments = {};
      const entityAssignments = {};

      Object.entries(selectedPropertyData).forEach(([source, valueSet]) => {
        if (valueSet.size > 0) {
          propertyAssignments[source] = Array.from(valueSet);
        }
      });

      Object.entries(selectedEntityData).forEach(([source, valueSet]) => {
        if (valueSet.size > 0) {
          entityAssignments[source] = Array.from(valueSet);
        }
      });

      console.log("Saving assignments:", {
        propertyAssignments,
        entityAssignments,
      });
      onSave({ propertyAssignments, entityAssignments });
    };

    return (
      <Box sx={{ p: 2 }}>
        {Object.entries(data).map(([source, sourceData]) => (
          <Card key={source} sx={{ mb: 2 }} variant="outlined">
            <CardContent>
              <Typography variant="subtitle1" color="primary" gutterBottom>
                {source}
              </Typography>

              {/* Person Properties Section */}
              {sourceData.Person && (
                <Box sx={{ mb: 3 }}>
                  <Typography variant="subtitle2" gutterBottom>
                    Personal Information
                  </Typography>
                  <Grid container spacing={1}>
                    {Object.keys(sourceData.Person).map((property) => (
                      <Grid item key={property}>
                        <Chip
                          label={property}
                          clickable
                          color={
                            isPropertySelected(source, property)
                              ? "primary"
                              : "default"
                          }
                          variant={
                            isPropertySelected(source, property)
                              ? "filled"
                              : "outlined"
                          }
                          onClick={() =>
                            togglePropertySelection(source, property)
                          }
                        />
                      </Grid>
                    ))}
                  </Grid>
                </Box>
              )}

              {/* Entity Records Section */}
              {Object.entries(sourceData)
                .filter(([entityType]) => entityType !== "Person")
                .map(([entityType, entityData]) => {
                  if (!Array.isArray(entityData)) return null;

                  return (
                    <Box key={entityType} sx={{ mb: 2 }}>
                      <Typography variant="subtitle2" gutterBottom>
                        {entityType} Records
                      </Typography>
                      <Grid container spacing={1}>
                        {entityData.map((entity, index) => {
                          const displayText = `${entityType} #${index + 1}`;
                          const previewText = Object.entries(entity.properties)
                            .slice(0, 2)
                            .map(([key, value]) => `${key}: ${value}`)
                            .join(", ");

                          return (
                            <Grid item xs={12} sm={6} key={entity.entityId}>
                              <Card
                                variant="outlined"
                                sx={{
                                  cursor: "pointer",
                                  bgcolor: isEntitySelected(
                                    source,
                                    entity.entityId
                                  )
                                    ? "primary.50"
                                    : "background.paper",
                                  borderColor: isEntitySelected(
                                    source,
                                    entity.entityId
                                  )
                                    ? "primary.main"
                                    : "divider",
                                  "&:hover": {
                                    bgcolor: isEntitySelected(
                                      source,
                                      entity.entityId
                                    )
                                      ? "primary.100"
                                      : "action.hover",
                                  },
                                }}
                                onClick={() =>
                                  toggleEntitySelection(source, entity.entityId)
                                }
                              >
                                <CardContent
                                  sx={{ p: 2, "&:last-child": { pb: 2 } }}
                                >
                                  <Box
                                    sx={{
                                      display: "flex",
                                      alignItems: "center",
                                      justifyContent: "space-between",
                                    }}
                                  >
                                    <Box>
                                      <Typography variant="subtitle2">
                                        {displayText}
                                      </Typography>
                                      <Typography
                                        variant="caption"
                                        color="text.secondary"
                                      >
                                        {previewText}...
                                      </Typography>
                                    </Box>
                                    <Checkbox
                                      checked={isEntitySelected(
                                        source,
                                        entity.entityId
                                      )}
                                      size="small"
                                    />
                                  </Box>
                                </CardContent>
                              </Card>
                            </Grid>
                          );
                        })}
                      </Grid>
                    </Box>
                  );
                })}
            </CardContent>
          </Card>
        ))}

        <Box
          sx={{ mt: 3, display: "flex", gap: 2, justifyContent: "flex-end" }}
        >
          <Button onClick={onCancel}>Cancel</Button>
          <Button
            onClick={handleSave}
            variant="contained"
            disabled={
              Object.keys(selectedPropertyData).length === 0 &&
              Object.keys(selectedEntityData).length === 0
            }
          >
            Assign Policy
          </Button>
        </Box>
      </Box>
    );
  }
);

// Template selection dialog component
const TemplateSelectionDialog = ({ open, onClose, templates, onSelect }) => {
  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>Select Privacy Tier Template</DialogTitle>
      <DialogContent>
        <Typography variant="body2" color="text.secondary" paragraph>
          Choose a privacy tier template based on how sensitive you consider
          your data. You can customize it further after selection.
        </Typography>
        <Grid container spacing={2}>
          {templates.map((template, index) => (
            <Grid item xs={12} md={6} key={index}>
              <Card
                sx={{
                  height: "100%",
                  display: "flex",
                  flexDirection: "column",
                }}
              >
                <CardContent sx={{ flexGrow: 1 }}>
                  <Typography variant="h6" gutterBottom>
                    {template.name}
                  </Typography>
                  <Typography variant="body2" color="text.secondary" paragraph>
                    {template.description}
                  </Typography>
                  <Typography variant="subtitle2" gutterBottom>
                    Permissions:
                  </Typography>
                  <Box sx={{ mb: 1 }}>
                    {Object.entries(template.permissions)
                      .filter(([_, value]) => value)
                      .map(([key]) => (
                        <Chip
                          key={key}
                          label={key.charAt(0).toUpperCase() + key.slice(1)}
                          size="small"
                          sx={{ mr: 0.5, mb: 0.5 }}
                        />
                      ))}
                  </Box>

                  {template.aiRestrictions?.allowAiTraining === false && (
                    <Typography variant="body2" color="error" sx={{ mt: 1 }}>
                      AI Training: Not Allowed
                    </Typography>
                  )}
                </CardContent>
                <CardActions>
                  <Button
                    fullWidth
                    variant="outlined"
                    onClick={() => onSelect(template)}
                  >
                    Use Template
                  </Button>
                </CardActions>
              </Card>
            </Grid>
          ))}
        </Grid>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
      </DialogActions>
    </Dialog>
  );
};

const PolicyGroupsManager = ({
  data,
  userId,
  initialSelectedData,
  selectedDataForPolicy,
}) => {
  const [policyGroups, setPolicyGroups] = useState([]);
  const [templates, setTemplates] = useState([]);
  const [selectedGroup, setSelectedGroup] = useState(null);
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [assignDialogOpen, setAssignDialogOpen] = useState(false);
  const [templateDialogOpen, setTemplateDialogOpen] = useState(false);
  const [formState, setFormState] = useState(initialFormState);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [selectedData, setSelectedData] = useState({});
  const [snackbar, setSnackbar] = useState({
    open: false,
    message: "",
    severity: "info",
  });

  useEffect(() => {
    if (userId) {
      fetchPolicyGroups();
      fetchTemplates();
    }
  }, [userId]);

  const fetchPolicyGroups = async () => {
    setLoading(true);
    try {
      const response = await fetch(
        `http://localhost:8080/api/policy-groups/${userId}`
      );
      if (!response.ok) throw new Error("Failed to fetch policies");
      const data = await response.json();
      setPolicyGroups(data);
    } catch (err) {
      setError(err.message);
      setSnackbar({
        open: true,
        message: `Error: ${err.message}`,
        severity: "error",
      });
    } finally {
      setLoading(false);
    }
  };

  const fetchTemplates = async () => {
    try {
      const response = await fetch(
        "http://localhost:8080/api/policy-groups/templates"
      );
      if (response.ok) {
        const data = await response.json();
        setTemplates(data);
      }
    } catch (error) {
      console.error("Error fetching templates:", error);
    }
  };

  const handleCreateGroup = async () => {
    setLoading(true);
    try {
      const response = await fetch(
        `http://localhost:8080/api/policy-groups?subjectId=${userId}`,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify(formState),
        }
      );

      if (!response.ok) throw new Error("Failed to create policy");

      // Refresh groups list
      await fetchPolicyGroups();

      setSnackbar({
        open: true,
        message: "Policy created successfully",
        severity: "success",
      });
      setCreateDialogOpen(false);
      setFormState(initialFormState);
    } catch (err) {
      setError(err.message);
      setSnackbar({
        open: true,
        message: `Error: ${err.message}`,
        severity: "error",
      });
    } finally {
      setLoading(false);
    }
  };

  const handleUpdateGroup = async () => {
    setLoading(true);
    try {
      const realGroupId = formState.id.includes("#")
        ? formState.id.split("#")[1]
        : formState.id;

      const response = await fetch(
        `http://localhost:8080/api/policy-groups/${realGroupId}?subjectId=${userId}`,
        {
          method: "PUT",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify(formState),
        }
      );

      if (!response.ok) throw new Error("Failed to update policy");

      // Refresh groups list
      await fetchPolicyGroups();

      setSnackbar({
        open: true,
        message: "Policy updated successfully",
        severity: "success",
      });
      setCreateDialogOpen(false);
      setFormState(initialFormState);
    } catch (err) {
      setError(err.message);
      setSnackbar({
        open: true,
        message: `Error: ${err.message}`,
        severity: "error",
      });
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteGroup = async (groupId) => {
    setLoading(true);
    try {
      // Extract the real ID from the policy#pg-... format
      const realGroupId = groupId.includes("#")
        ? groupId.split("#")[1]
        : groupId;

      const response = await fetch(
        `http://localhost:8080/api/policy-groups/${realGroupId}?subjectId=${userId}`,
        {
          method: "DELETE",
        }
      );

      if (!response.ok) throw new Error("Failed to delete policy");

      // Refresh groups list
      await fetchPolicyGroups();

      setSnackbar({
        open: true,
        message: "Policy deleted successfully",
        severity: "success",
      });
    } catch (err) {
      setError(err.message);
      setSnackbar({
        open: true,
        message: `Error: ${err.message}`,
        severity: "error",
      });
    } finally {
      setLoading(false);
    }
  };

  const handleEditGroup = (group) => {
    setFormState(group);
    setCreateDialogOpen(true);
  };

  const handleAssignGroup = async (group) => {
    setSelectedGroup(group);

    // Extract the real ID from the policy URI format
    let cleanedId = group.id;
    if (cleanedId.includes("#")) {
      cleanedId = cleanedId.substring(cleanedId.lastIndexOf("#") + 1);
    } else if (cleanedId.includes("/")) {
      cleanedId = cleanedId.substring(cleanedId.lastIndexOf("/") + 1);
    }

    try {
      setLoading(true);

      // Fetch current assignments
      const response = await fetch(
        `http://localhost:8080/api/policy-groups/${cleanedId}/assignments?subjectId=${userId}`
      );

      let formattedAssignments = {
        properties: {},
        entities: {},
      };

      if (response.ok) {
        const currentAssignments = await response.json();
        console.log("Fetched current assignments:", currentAssignments);

        // Handle the new backend format: {propertyAssignments: {...}, entityAssignments: {...}}
        if (currentAssignments.propertyAssignments) {
          Object.entries(currentAssignments.propertyAssignments).forEach(
            ([source, properties]) => {
              if (properties && properties.length > 0) {
                formattedAssignments.properties[source] = new Set(properties);
              }
            }
          );
        }

        if (currentAssignments.entityAssignments) {
          Object.entries(currentAssignments.entityAssignments).forEach(
            ([source, entities]) => {
              if (entities && entities.length > 0) {
                formattedAssignments.entities[source] = new Set(entities);
              }
            }
          );
        }
      } else {
        console.warn("Failed to fetch current assignments, starting fresh");
      }

      // Merge with initialSelectedData if provided (for context-specific assignment)
      if (initialSelectedData) {
        if (initialSelectedData.properties) {
          Object.entries(initialSelectedData.properties).forEach(
            ([source, properties]) => {
              if (!formattedAssignments.properties[source]) {
                formattedAssignments.properties[source] = new Set();
              }
              const propsToAdd = Array.isArray(properties)
                ? properties
                : [properties];
              propsToAdd.forEach((prop) =>
                formattedAssignments.properties[source].add(prop)
              );
            }
          );
        }

        if (initialSelectedData.entities) {
          Object.entries(initialSelectedData.entities).forEach(
            ([source, entities]) => {
              if (!formattedAssignments.entities[source]) {
                formattedAssignments.entities[source] = new Set();
              }
              const entitiesToAdd = Array.isArray(entities)
                ? entities
                : [entities];
              entitiesToAdd.forEach((entity) =>
                formattedAssignments.entities[source].add(entity)
              );
            }
          );
        }
      }

      console.log(
        "Final formatted assignments for form:",
        formattedAssignments
      );
      setSelectedData(formattedAssignments);
    } catch (error) {
      console.error("Error fetching assignments:", error);
      // Fallback to initialSelectedData or empty state
      const fallbackData = initialSelectedData || {
        properties: {},
        entities: {},
      };
      setSelectedData(fallbackData);
    } finally {
      setLoading(false);
      setAssignDialogOpen(true);
    }
  };

  const handleAssignData = async (assignmentData) => {
    setLoading(true);
    try {
      // Extract the real ID from the policy#pg-... format
      const realGroupId = selectedGroup.id.includes("#")
        ? selectedGroup.id.split("#")[1]
        : selectedGroup.id;
      console.log(`Using cleaned ID: ${realGroupId}`);
      console.log(
        "Sending assignment data:",
        JSON.stringify(assignmentData, null, 2)
      );

      const response = await fetch(
        `http://localhost:8080/api/policy-groups/${realGroupId}/assign?subjectId=${userId}`,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify(assignmentData), // Now sends {propertyAssignments, entityAssignments}
        }
      );

      if (!response.ok) throw new Error("Failed to assign data to policy");
      setSnackbar({
        open: true,
        message: "Data assigned to policy successfully",
        severity: "success",
      });
      setAssignDialogOpen(false);
      setSelectedGroup(null);
    } catch (err) {
      setError(err.message);
      setSnackbar({
        open: true,
        message: `Error: ${err.message}`,
        severity: "error",
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box sx={{ width: "100%", maxWidth: 800, mx: "auto", p: 3 }}>
      {loading && (
        <Box sx={{ display: "flex", justifyContent: "center", mt: 2, mb: 2 }}>
          <CircularProgress />
        </Box>
      )}

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      <Box sx={{ display: "flex", justifyContent: "space-between", mb: 3 }}>
        <Typography variant="h5">Policies</Typography>
        <Box sx={{ display: "flex", gap: 1.5 }}>
          <Button
            variant="outlined"
            startIcon={<LayersIcon />}
            onClick={() => setTemplateDialogOpen(true)}
          >
            Use Template
          </Button>
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={() => {
              setFormState(initialFormState);
              setCreateDialogOpen(true);
            }}
            sx={{ boxShadow: 2 }}
          >
            Create Policy
          </Button>
        </Box>
      </Box>

      {policyGroups.length === 0 && !loading ? (
        <Card
          sx={{
            textAlign: "center",
            py: 6,
            bgcolor: "grey.50",
            boxShadow: 1,
          }}
        >
          <CardContent>
            <PolicyIcon sx={{ fontSize: 64, color: "primary.light", mb: 2 }} />
            <Typography variant="h6" gutterBottom fontWeight={600}>
              No Policies Yet
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
              Create your first policy to start managing data access and privacy
            </Typography>
            <Button
              variant="contained"
              startIcon={<AddIcon />}
              onClick={() => {
                setFormState(initialFormState);
                setCreateDialogOpen(true);
              }}
              size="large"
            >
              Create Your First Policy
            </Button>
          </CardContent>
        </Card>
      ) : (
        <List>
          {policyGroups.map((group) => (
            <Card
              key={group.id}
              sx={{
                mb: 2,
                boxShadow: 2,
                transition: "all 0.2s ease-in-out",
                "&:hover": {
                  boxShadow: 4,
                  transform: "translateY(-2px)",
                },
              }}
            >
              <CardContent>
                <Box
                  sx={{
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "flex-start",
                  }}
                >
                  <Box sx={{ flex: 1 }}>
                    <Typography variant="h6" gutterBottom fontWeight={600}>
                      {group.name}
                    </Typography>
                    <Typography
                      variant="body2"
                      color="text.secondary"
                      paragraph
                    >
                      {group.description}
                    </Typography>
                    <Box
                      sx={{
                        display: "flex",
                        flexWrap: "wrap",
                        gap: 0.5,
                      }}
                    >
                      {Object.entries(group.permissions)
                        .filter(([_, value]) => value)
                        .map(([key]) => (
                          <Chip
                            key={key}
                            label={key.charAt(0).toUpperCase() + key.slice(1)}
                            size="small"
                            color="primary"
                            variant="outlined"
                          />
                        ))}

                      {group.aiRestrictions?.allowAiTraining === false && (
                        <Chip
                          label="No AI Training"
                          size="small"
                          color="error"
                          variant="outlined"
                        />
                      )}

                      {group.transformations && group.transformations.length > 0 && (
                        group.transformations.map((transformation) => (
                          <Chip
                            key={transformation}
                            label={transformation.charAt(0).toUpperCase() + transformation.slice(1)}
                            size="small"
                            color="warning"
                            variant="outlined"
                          />
                        ))
                      )}
                    </Box>
                  </Box>
                  <Box sx={{ display: "flex", gap: 1, ml: 2 }}>
                    <Tooltip title="Edit policy">
                      <IconButton
                        onClick={() => handleEditGroup(group)}
                        size="small"
                      >
                        <EditIcon />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="Delete policy">
                      <IconButton
                        onClick={() => handleDeleteGroup(group.id)}
                        color="error"
                        size="small"
                      >
                        <DeleteIcon />
                      </IconButton>
                    </Tooltip>
                    <Button
                      variant="outlined"
                      size="small"
                      onClick={() => handleAssignGroup(group)}
                    >
                      Assign Data
                    </Button>
                  </Box>
                </Box>
              </CardContent>
            </Card>
          ))}
        </List>
      )}

      <Dialog
        open={createDialogOpen}
        onClose={() => {
          setCreateDialogOpen(false);
          setFormState(initialFormState);
        }}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>
          {formState.id ? "Edit Policy" : "Create Policy"}
        </DialogTitle>
        <DialogContent>
          <PolicyGroupForm
            formState={formState}
            setFormState={setFormState}
            onSave={formState.id ? handleUpdateGroup : handleCreateGroup}
            onCancel={() => {
              setCreateDialogOpen(false);
              setFormState(initialFormState);
            }}
          />
        </DialogContent>
      </Dialog>

      <Dialog
        open={assignDialogOpen}
        onClose={() => {
          setAssignDialogOpen(false);
          setSelectedGroup(null);
        }}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>Assign Data to {selectedGroup?.name}</DialogTitle>
        <DialogContent>
          <AssignPolicyForm
            data={data}
            groupName={selectedGroup?.name}
            initialSelectedData={selectedData}
            onSave={handleAssignData}
            onCancel={() => {
              setAssignDialogOpen(false);
              setSelectedGroup(null);
            }}
          />
        </DialogContent>
      </Dialog>

      <TemplateSelectionDialog
        open={templateDialogOpen}
        onClose={() => setTemplateDialogOpen(false)}
        templates={templates}
        onSelect={(template) => {
          setFormState(template);
          setTemplateDialogOpen(false);
          setCreateDialogOpen(true);
        }}
      />

      <Snackbar
        open={snackbar.open}
        autoHideDuration={6000}
        onClose={() => setSnackbar({ ...snackbar, open: false })}
      >
        <Alert
          onClose={() => setSnackbar({ ...snackbar, open: false })}
          severity={snackbar.severity}
        >
          {snackbar.message}
        </Alert>
      </Snackbar>
    </Box>
  );
};

export default PolicyGroupsManager;
