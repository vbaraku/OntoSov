import React, { useState, useEffect } from "react";
import {
  Box,
  TextField,
  MenuItem,
  Button,
  Paper,
  Typography,
  Alert,
  CircularProgress,
  Chip,
  Divider,
  List,
  ListItem,
  ListItemText,
  IconButton,
  Snackbar,
  Grid,
  Card,
  CardContent,
  FormControl,
  InputLabel,
  Select,
  Autocomplete,
  Tabs,
  Tab,
} from "@mui/material";
import {
  Send as SendIcon,
  CheckCircle as CheckCircleIcon,
  Cancel as CancelIcon,
  ContentCopy as CopyIcon,
  Info as InfoIcon,
} from "@mui/icons-material";

const ACTIONS = ["read", "use", "share", "aggregate", "modify", "aiTraining"];

const PURPOSE_OPTIONS = [
  "Service Provision",
  "Necessary Processing",
  "Essential Services Only",
  "Research",
  "Marketing",
];

const AI_ALGORITHM_OPTIONS = [
  { value: "", label: "Any Algorithm" },
  { value: "federatedLearning", label: "Federated Learning" },
  { value: "differentialPrivacy", label: "Differential Privacy" },
  { value: "secureEnclave", label: "Secure Enclave Processing" },
  { value: "localProcessing", label: "Local Processing Only" },
];

const PolicyChecker = ({ controllerId }) => {
  const [currentTab, setCurrentTab] = useState(0); // 0 = Property, 1 = Entity

  const [formData, setFormData] = useState({
    subjectTaxId: "",
    action: "read",
    purpose: "",
    aiAlgorithm: "",
    dataSource: "",
    tableName: "",
    dataProperty: "", // For property tab
    recordId: "",     // For entity tab
    dataDescription: "",
  });

  const [databases, setDatabases] = useState([]);
  const [tables, setTables] = useState([]);
  const [properties, setProperties] = useState([]);
  const [decision, setDecision] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [validationErrors, setValidationErrors] = useState({});
  const [snackbarOpen, setSnackbarOpen] = useState(false);

  // Fetch databases when component mounts
  useEffect(() => {
    if (controllerId) {
      fetchDatabases();
    }
  }, [controllerId]);

  const fetchDatabases = async () => {
    try {
      const response = await fetch(
        `http://localhost:8080/api/database/controller/${controllerId}/databases`
      );
      if (response.ok) {
        const data = await response.json();
        setDatabases(data);
      }
    } catch (err) {
      console.error("Error fetching databases:", err);
    }
  };

  const handleDatabaseChange = async (dbId) => {
    setFormData({
      ...formData,
      dataSource: dbId,
      tableName: "",
      dataProperty: "",
      recordId: "",
    });
    setTables([]);
    setProperties([]);

    if (!dbId) return;

    try {
      const selectedDb = databases.find((d) => d.id === dbId);
      if (!selectedDb) return;

      const response = await fetch(
        "http://localhost:8080/api/database/tables",
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(selectedDb),
        }
      );

      if (response.ok) {
        const tablesData = await response.json();
        setTables(tablesData);
      }
    } catch (err) {
      console.error("Error fetching tables:", err);
    }
  };

  const handleTableChange = (tableName) => {
    setFormData({
      ...formData,
      tableName: tableName,
      dataProperty: "",
    });
    setProperties([]);

    if (!tableName || currentTab === 1) return; // Don't fetch properties for entity tab

    // Find the selected table
    const selectedTable = tables.find((t) => t.name === tableName);
    if (selectedTable && selectedTable.columns) {
      setProperties(selectedTable.columns.map((col) => col.name));
    }
  };

  const handleChange = (field) => (event) => {
    setFormData({ ...formData, [field]: event.target.value });
    // Clear validation error for this field
    if (validationErrors[field]) {
      setValidationErrors({ ...validationErrors, [field]: null });
    }
  };

  const handleTabChange = (event, newValue) => {
    setCurrentTab(newValue);
    setDecision(null); // Clear previous decision
    setValidationErrors({}); // Clear validation errors
    // Reset column/recordId based on tab
    if (newValue === 0) {
      setFormData({ ...formData, recordId: "", dataProperty: "" });
    } else {
      setFormData({ ...formData, dataProperty: "", recordId: "" });
    }
  };

  const validate = () => {
    const errors = {};

    if (!formData.subjectTaxId.trim()) {
      errors.subjectTaxId = "Subject Tax ID is required";
    }

    if (!formData.dataSource) {
      errors.dataSource = "Data Source is required";
    }

    if (!formData.tableName) {
      errors.tableName = "Table is required";
    }

    // Property tab validation
    if (currentTab === 0) {
      if (!formData.dataProperty) {
        errors.dataProperty = "Column is required for property-level checks";
      }
    }

    // Entity tab validation
    if (currentTab === 1) {
      if (!formData.recordId || !formData.recordId.trim()) {
        errors.recordId = "Record ID is required for entity-level checks";
      }
    }

    if (!formData.action) {
      errors.action = "Action is required";
    }

    // Purpose validation for non-aiTraining actions
    if (formData.action !== "aiTraining") {
      if (!formData.purpose || !formData.purpose.trim()) {
        errors.purpose = "Purpose is required";
      } else if (formData.purpose.trim().length < 10) {
        errors.purpose = "Purpose must be at least 10 characters";
      }
    }

    setValidationErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (!validate()) return;

    setLoading(true);
    setError(null);
    setDecision(null);

    try {
      const requestBody = {
        controllerId,
        subjectTaxId: formData.subjectTaxId,
        action: formData.action,
        dataSource: formData.dataSource,
        tableName: formData.tableName,
        dataDescription: formData.dataDescription,
      };

      // Add property OR recordId based on tab
      if (currentTab === 0) {
        requestBody.dataProperty = formData.dataProperty;
      } else {
        requestBody.recordId = formData.recordId;
      }

      // Add purpose only for non-aiTraining actions
      if (formData.action !== "aiTraining") {
        requestBody.purpose = formData.purpose;
      }

      // Add aiAlgorithm only for aiTraining action
      if (formData.action === "aiTraining") {
        requestBody.aiAlgorithm = formData.aiAlgorithm || null;
      }

      console.log("Submitting request:", requestBody);

      const response = await fetch(
        "http://localhost:8080/api/controller/check-access",
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(requestBody),
        }
      );

      if (!response.ok) {
        throw new Error("Failed to check access");
      }

      const result = await response.json();
      setDecision(result);
    } catch (err) {
      console.error("Error checking access:", err);
      setError("Failed to check access. Please try again.");
    } finally {
      setLoading(false);
    }
  };

  const handleReset = () => {
    setFormData({
      subjectTaxId: "",
      action: "read",
      purpose: "",
      aiAlgorithm: "",
      dataSource: "",
      tableName: "",
      dataProperty: "",
      recordId: "",
      dataDescription: "",
    });
    setTables([]);
    setProperties([]);
    setValidationErrors({});
    setDecision(null);
  };

  const copyToClipboard = (text) => {
    navigator.clipboard.writeText(text);
    setSnackbarOpen(true);
  };

  const isPermit = decision?.result === "PERMIT";
  const isAiTraining = formData.action === "aiTraining";

  return (
    <Box>
      <Typography variant="h5" color="text.primary" gutterBottom>
        Policy Checker
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        Request access to subject data and receive immediate policy-based
        decisions.
      </Typography>

      <Alert severity="info" sx={{ mb: 3 }}>
        <Typography variant="body2">
          <strong>How it works:</strong> Enter the subject's tax ID and select
          the specific data you need. The system will evaluate your request
          against the subject's policies and provide an instant decision.
        </Typography>
      </Alert>

      <Grid container spacing={3}>
        <Grid item xs={12} md={6}>
          <Paper elevation={2} sx={{ p: 3 }}>
            <Typography variant="h6" gutterBottom>
              Access Request Form
            </Typography>

            {/* TABS */}
            <Tabs
              value={currentTab}
              onChange={handleTabChange}
              sx={{ borderBottom: 1, borderColor: 'divider', mb: 2 }}
            >
              <Tab label="Property Access" />
              <Tab label="Entity Access" />
            </Tabs>

            {error && (
              <Alert severity="error" sx={{ mb: 2 }}>
                {error}
              </Alert>
            )}

            <Box component="form" onSubmit={handleSubmit} noValidate>
              {/* Common fields for both tabs */}
              <TextField
                fullWidth
                label="Subject Tax ID"
                value={formData.subjectTaxId}
                onChange={handleChange("subjectTaxId")}
                error={!!validationErrors.subjectTaxId}
                helperText={
                  validationErrors.subjectTaxId ||
                  "Enter the tax ID of the data subject"
                }
                margin="normal"
                required
                disabled={loading}
              />

              <FormControl
                fullWidth
                margin="normal"
                required
                error={!!validationErrors.dataSource}
                disabled={loading}
              >
                <InputLabel>Data Source</InputLabel>
                <Select
                  value={formData.dataSource}
                  onChange={(e) => handleDatabaseChange(e.target.value)}
                  label="Data Source"
                >
                  <MenuItem value="">
                    <em>Select a database</em>
                  </MenuItem>
                  {databases.map((db) => (
                    <MenuItem key={db.id} value={db.id}>
                      {db.databaseName} ({db.databaseType?.toUpperCase()})
                    </MenuItem>
                  ))}
                </Select>
                {validationErrors.dataSource ? (
                  <Typography
                    variant="caption"
                    color="error"
                    sx={{ mt: 0.5, ml: 2 }}
                  >
                    {validationErrors.dataSource}
                  </Typography>
                ) : (
                  <Typography
                    variant="caption"
                    color="text.secondary"
                    sx={{ mt: 0.5, ml: 2 }}
                  >
                    Select which database contains the data
                  </Typography>
                )}
              </FormControl>

              <FormControl
                fullWidth
                margin="normal"
                required
                error={!!validationErrors.tableName}
                disabled={loading || !formData.dataSource}
              >
                <InputLabel>Table</InputLabel>
                <Select
                  value={formData.tableName}
                  onChange={(e) => handleTableChange(e.target.value)}
                  label="Table"
                >
                  <MenuItem value="">
                    <em>Select a table</em>
                  </MenuItem>
                  {tables.map((table) => (
                    <MenuItem key={table.name} value={table.name}>
                      {table.name}
                    </MenuItem>
                  ))}
                </Select>
                {validationErrors.tableName ? (
                  <Typography
                    variant="caption"
                    color="error"
                    sx={{ mt: 0.5, ml: 2 }}
                  >
                    {validationErrors.tableName}
                  </Typography>
                ) : (
                  <Typography
                    variant="caption"
                    color="text.secondary"
                    sx={{ mt: 0.5, ml: 2 }}
                  >
                    {formData.dataSource
                      ? "Select which table contains the data"
                      : "Select a database first"}
                  </Typography>
                )}
              </FormControl>

              {/* TAB-SPECIFIC FIELDS */}
              {currentTab === 0 ? (
                // PROPERTY TAB - Column dropdown
                <FormControl
                  fullWidth
                  margin="normal"
                  required
                  error={!!validationErrors.dataProperty}
                  disabled={loading || !formData.tableName}
                >
                  <InputLabel>Column</InputLabel>
                  <Select
                    value={formData.dataProperty}
                    onChange={handleChange("dataProperty")}
                    label="Column"
                  >
                    <MenuItem value="">
                      <em>Select a column</em>
                    </MenuItem>
                    {properties.map((prop) => (
                      <MenuItem key={prop} value={prop}>
                        {prop}
                      </MenuItem>
                    ))}
                  </Select>
                  {validationErrors.dataProperty && (
                    <Typography
                      variant="caption"
                      color="error"
                      sx={{ mt: 0.5, ml: 2 }}
                    >
                      {validationErrors.dataProperty}
                    </Typography>
                  )}
                  {!validationErrors.dataProperty && (
                    <Typography
                      variant="caption"
                      color="text.secondary"
                      sx={{ mt: 0.5, ml: 2 }}
                    >
                      Select which column to access
                    </Typography>
                  )}
                </FormControl>
              ) : (
                // ENTITY TAB - Record ID text field
                <TextField
                  fullWidth
                  label="Record ID"
                  value={formData.recordId}
                  onChange={handleChange("recordId")}
                  error={!!validationErrors.recordId}
                  helperText={
                    validationErrors.recordId ||
                    "Enter the primary key value of the specific record (e.g., order ID, medical record ID)"
                  }
                  margin="normal"
                  required
                  disabled={loading || !formData.tableName}
                  placeholder="e.g., 1234"
                />
              )}

              {/* Action selection */}
              <FormControl
                fullWidth
                margin="normal"
                required
                error={!!validationErrors.action}
                disabled={loading}
              >
                <InputLabel>Action</InputLabel>
                <Select
                  value={formData.action}
                  onChange={handleChange("action")}
                  label="Action"
                >
                  {ACTIONS.map((action) => (
                    <MenuItem key={action} value={action}>
                      {action}
                    </MenuItem>
                  ))}
                </Select>
                {validationErrors.action ? (
                  <Typography
                    variant="caption"
                    color="error"
                    sx={{ mt: 0.5, ml: 2 }}
                  >
                    {validationErrors.action}
                  </Typography>
                ) : (
                  <Typography
                    variant="caption"
                    color="text.secondary"
                    sx={{ mt: 0.5, ml: 2 }}
                  >
                    Select what operation you want to perform
                  </Typography>
                )}
              </FormControl>

              {/* Purpose or AI Algorithm */}
              {!isAiTraining ? (
                <Autocomplete
                  freeSolo
                  options={PURPOSE_OPTIONS}
                  value={formData.purpose}
                  onChange={(event, newValue) => {
                    setFormData({ ...formData, purpose: newValue || "" });
                    if (validationErrors.purpose) {
                      setValidationErrors({
                        ...validationErrors,
                        purpose: null,
                      });
                    }
                  }}
                  onInputChange={(event, newInputValue) => {
                    setFormData({ ...formData, purpose: newInputValue });
                    if (validationErrors.purpose) {
                      setValidationErrors({
                        ...validationErrors,
                        purpose: null,
                      });
                    }
                  }}
                  disabled={loading}
                  renderInput={(params) => (
                    <TextField
                      {...params}
                      label="Purpose"
                      margin="normal"
                      required
                      error={!!validationErrors.purpose}
                      helperText={
                        validationErrors.purpose ||
                        "Select a preset or type your own purpose (min 10 characters)"
                      }
                      placeholder="e.g., Service Provision"
                    />
                  )}
                />
              ) : (
                <FormControl fullWidth margin="normal" disabled={loading}>
                  <InputLabel>AI Algorithm (Optional)</InputLabel>
                  <Select
                    value={formData.aiAlgorithm}
                    onChange={handleChange("aiAlgorithm")}
                    label="AI Algorithm (Optional)"
                  >
                    {AI_ALGORITHM_OPTIONS.map((option) => (
                      <MenuItem key={option.value} value={option.value}>
                        {option.label}
                      </MenuItem>
                    ))}
                  </Select>
                  <Typography
                    variant="caption"
                    color="text.secondary"
                    sx={{ mt: 0.5, ml: 2 }}
                  >
                    Specify which AI algorithm you'll use (leave blank for any)
                  </Typography>
                </FormControl>
              )}

              <TextField
                fullWidth
                label="Data Description (Optional)"
                value={formData.dataDescription}
                onChange={handleChange("dataDescription")}
                helperText="Additional context about this request (for logging)"
                margin="normal"
                multiline
                rows={2}
                disabled={loading}
                placeholder="e.g., email from patients table for treatment follow-up"
              />

              <Box sx={{ mt: 3, display: "flex", gap: 2 }}>
                <Button
                  type="submit"
                  variant="contained"
                  startIcon={
                    loading ? <CircularProgress size={20} /> : <SendIcon />
                  }
                  disabled={loading}
                  fullWidth
                >
                  {loading ? "Checking..." : "Check Access"}
                </Button>
                <Button
                  variant="outlined"
                  onClick={handleReset}
                  disabled={loading}
                >
                  Reset
                </Button>
              </Box>
            </Box>
          </Paper>
        </Grid>

        <Grid item xs={12} md={6} sx={{ display: "flex" }}>
          <Box sx={{ width: "100%", display: "flex", flexDirection: "column" }}>
            {decision ? (
              <Paper
                elevation={3}
                sx={{
                  p: 3,
                  border: 2,
                  borderColor: isPermit ? "success.main" : "error.main",
                  flexGrow: 1,
                  display: "flex",
                  flexDirection: "column",
                }}
              >
                <Box sx={{ display: "flex", alignItems: "center", mb: 2 }}>
                  {isPermit ? (
                    <CheckCircleIcon
                      sx={{ fontSize: 48, color: "success.main", mr: 2 }}
                    />
                  ) : (
                    <CancelIcon
                      sx={{ fontSize: 48, color: "error.main", mr: 2 }}
                    />
                  )}
                  <Box>
                    <Typography variant="h5" component="div">
                      Access {isPermit ? "Permitted" : "Denied"}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      Decision: {decision.result}
                    </Typography>
                  </Box>
                </Box>

                <Divider sx={{ my: 2 }} />

                <Box sx={{ flexGrow: 1 }}>
                  <List dense>
                    <ListItem>
                      <ListItemText
                        primary="Reason"
                        secondary={decision.reason}
                        secondaryTypographyProps={{
                          sx: { whiteSpace: "pre-wrap" },
                        }}
                      />
                    </ListItem>

                    {decision.policyGroupId && (
                      <ListItem>
                        <ListItemText
                          primary="Policy Group"
                          secondary={decision.policyGroupId}
                        />
                        <IconButton
                          size="small"
                          onClick={() => copyToClipboard(decision.policyGroupId)}
                        >
                          <CopyIcon fontSize="small" />
                        </IconButton>
                      </ListItem>
                    )}

                    {decision.obligations && decision.obligations.length > 0 && (
                      <>
                        <Divider sx={{ my: 1 }} />
                        <ListItem>
                          <ListItemText
                            primary="Obligations"
                            secondary={
                              <Box sx={{ mt: 1 }}>
                                {decision.obligations.map((obligation, index) => (
                                  <Chip
                                    key={index}
                                    label={obligation.action}
                                    size="small"
                                    color="warning"
                                    sx={{ mr: 1, mb: 1 }}
                                  />
                                ))}
                              </Box>
                            }
                          />
                        </ListItem>
                      </>
                    )}
                  </List>

                  {isPermit && (
                    <Alert severity="success" sx={{ mt: 2, mx: 2 }}>
                      <Typography variant="body2">
                        <strong>Next Steps:</strong> You may proceed with accessing
                        this data. All obligations listed above must be fulfilled.
                        This access attempt has been logged on the blockchain.
                      </Typography>
                    </Alert>
                  )}

                  {!isPermit && (
                    <Alert severity="error" sx={{ mt: 2, mx: 2 }}>
                      <Typography variant="body2">
                        <strong>Access Denied:</strong> The subject's policy does
                        not permit this access. This denial has been logged on the
                        blockchain for transparency.
                      </Typography>
                    </Alert>
                  )}
                </Box>
              </Paper>
            ) : (
              <Card sx={{ flexGrow: 1, display: "flex" }}>
                <CardContent
                  sx={{
                    display: "flex",
                    flexDirection: "column",
                    alignItems: "center",
                    justifyContent: "center",
                    width: "100%",
                  }}
                >
                  <InfoIcon sx={{ fontSize: 64, color: "action.disabled", mb: 2 }} />
                  <Typography variant="h6" color="text.secondary" gutterBottom>
                    No Decision Yet
                  </Typography>
                  <Typography
                    variant="body2"
                    color="text.secondary"
                    align="center"
                  >
                    Fill out the form and submit your access request to receive a
                    policy-based decision.
                  </Typography>
                </CardContent>
              </Card>
            )}
          </Box>
        </Grid>
      </Grid>

      <Snackbar
        open={snackbarOpen}
        autoHideDuration={2000}
        onClose={() => setSnackbarOpen(false)}
        message="Copied to clipboard"
      />
    </Box>
  );
};

export default PolicyChecker;