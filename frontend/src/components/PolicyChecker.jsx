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
} from "@mui/material";
import {
  Send as SendIcon,
  CheckCircle as CheckCircleIcon,
  Cancel as CancelIcon,
  ContentCopy as CopyIcon,
  Info as InfoIcon,
} from "@mui/icons-material";

const ACTIONS = ["read", "use", "share", "aggregate", "modify"];

const PolicyChecker = ({ controllerId }) => {
  const [formData, setFormData] = useState({
    subjectTaxId: "",
    action: "read",
    purpose: "",
    dataSource: "",
    dataProperty: "",
    dataDescription: "",
  });
  const [databases, setDatabases] = useState([]);
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
    setFormData({ ...formData, dataSource: dbId, dataProperty: "" });
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
        const tables = await response.json();

        // Extract all column names from all tables
        const allColumns = new Set();
        tables.forEach((table) => {
          table.columns.forEach((col) => allColumns.add(col.name));
        });

        setProperties(Array.from(allColumns).sort());
      }
    } catch (err) {
      console.error("Error fetching properties:", err);
    }
  };

  const handleChange = (field) => (event) => {
    setFormData({
      ...formData,
      [field]: event.target.value,
    });
    if (validationErrors[field]) {
      setValidationErrors({ ...validationErrors, [field]: null });
    }
  };

  const validate = () => {
    const errors = {};
    if (!formData.subjectTaxId.trim()) {
      errors.subjectTaxId = "Subject Tax ID is required";
    }
    if (!formData.dataSource) {
      errors.dataSource = "Data source is required";
    }
    if (!formData.dataProperty) {
      errors.dataProperty = "Data property is required";
    }
    if (!formData.purpose.trim()) {
      errors.purpose = "Purpose is required";
    } else if (formData.purpose.trim().length < 10) {
      errors.purpose = "Purpose must be at least 10 characters";
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
      const response = await fetch(
        "http://localhost:8080/api/controller/check-access",
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            controllerId,
            ...formData,
          }),
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
      dataSource: "",
      dataProperty: "",
      dataDescription: "",
    });
    setProperties([]);
    setValidationErrors({});
    setDecision(null);
  };

  const copyToClipboard = (text) => {
    navigator.clipboard.writeText(text);
    setSnackbarOpen(true);
  };

  const isPermit = decision?.result === "PERMIT";

  return (
    <Box>
      <Typography variant="h5" gutterBottom>
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

            {error && (
              <Alert severity="error" sx={{ mb: 2 }}>
                {error}
              </Alert>
            )}

            <Box component="form" onSubmit={handleSubmit} noValidate>
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
                {validationErrors.dataSource && (
                  <Typography
                    variant="caption"
                    color="error"
                    sx={{ mt: 0.5, ml: 2 }}
                  >
                    {validationErrors.dataSource}
                  </Typography>
                )}
                {!validationErrors.dataSource && (
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
                error={!!validationErrors.dataProperty}
                disabled={loading || !formData.dataSource}
              >
                <InputLabel>Data Property</InputLabel>
                <Select
                  value={formData.dataProperty}
                  onChange={handleChange("dataProperty")}
                  label="Data Property"
                >
                  <MenuItem value="">
                    <em>Select a property</em>
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
                    {formData.dataSource
                      ? "Select which property/column you need"
                      : "Select a data source first"}
                  </Typography>
                )}
              </FormControl>

              <TextField
                fullWidth
                select
                label="Action"
                value={formData.action}
                onChange={handleChange("action")}
                margin="normal"
                required
                disabled={loading}
                helperText="Select the type of data access you need"
              >
                {ACTIONS.map((action) => (
                  <MenuItem key={action} value={action}>
                    {action.charAt(0).toUpperCase() + action.slice(1)}
                  </MenuItem>
                ))}
              </TextField>

              <TextField
                fullWidth
                label="Purpose"
                value={formData.purpose}
                onChange={handleChange("purpose")}
                error={!!validationErrors.purpose}
                helperText={
                  validationErrors.purpose ||
                  'Explain why you need access (e.g., "Service Provision")'
                }
                margin="normal"
                required
                multiline
                rows={3}
                disabled={loading}
                placeholder="e.g., Service Provision for order fulfillment"
              />

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
                placeholder="e.g., email from ecommerce database"
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

        <Grid item xs={12} md={6}>
          {decision ? (
            <Paper
              elevation={3}
              sx={{
                p: 3,
                border: 2,
                borderColor: isPermit ? "success.main" : "error.main",
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
                    {new Date().toLocaleString()}
                  </Typography>
                </Box>
              </Box>

              <Divider sx={{ my: 2 }} />

              <Box sx={{ mb: 2 }}>
                <Typography
                  variant="subtitle2"
                  color="text.secondary"
                  gutterBottom
                >
                  Reason
                </Typography>
                <Alert
                  severity={isPermit ? "success" : "error"}
                  icon={<InfoIcon />}
                >
                  {decision.reason}
                </Alert>
              </Box>

              {decision.policyGroupId && (
                <Box sx={{ mb: 2 }}>
                  <Typography
                    variant="subtitle2"
                    color="text.secondary"
                    gutterBottom
                  >
                    Policy Evaluated
                  </Typography>
                  <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap" }}>
                    <Chip
                      label={`Policy: ${decision.policyGroupId}`}
                      size="small"
                      variant="outlined"
                    />
                    {decision.policyVersion && (
                      <Chip
                        label={`Version: ${decision.policyVersion}`}
                        size="small"
                        variant="outlined"
                      />
                    )}
                  </Box>
                </Box>
              )}

              {decision.obligations && decision.obligations.length > 0 && (
                <Box sx={{ mb: 2 }}>
                  <Typography
                    variant="subtitle2"
                    color="text.secondary"
                    gutterBottom
                  >
                    Obligations
                  </Typography>
                  <List dense>
                    {decision.obligations.map((obligation, index) => (
                      <ListItem key={index}>
                        <ListItemText
                          primary={obligation.type}
                          secondary={JSON.stringify(obligation.details)}
                        />
                      </ListItem>
                    ))}
                  </List>
                </Box>
              )}

              <Box sx={{ display: "flex", gap: 1, mt: 3 }}>
                <Button
                  variant="outlined"
                  size="small"
                  startIcon={<CopyIcon />}
                  onClick={() =>
                    copyToClipboard(JSON.stringify(decision, null, 2))
                  }
                >
                  Copy Response
                </Button>
              </Box>
            </Paper>
          ) : (
            <Card
              sx={{
                height: "100%",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              <CardContent sx={{ textAlign: "center" }}>
                <InfoIcon
                  sx={{ fontSize: 64, color: "text.secondary", mb: 2 }}
                />
                <Typography variant="h6" color="text.secondary">
                  Submit a request to see the decision
                </Typography>
              </CardContent>
            </Card>
          )}
        </Grid>
      </Grid>

      <Snackbar
        open={snackbarOpen}
        autoHideDuration={3000}
        onClose={() => setSnackbarOpen(false)}
        message="Copied to clipboard"
      />
    </Box>
  );
};

export default PolicyChecker;
