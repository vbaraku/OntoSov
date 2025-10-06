import React, { useState } from "react";
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
    dataDescription: "",
  });
  const [decision, setDecision] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [validationErrors, setValidationErrors] = useState({});
  const [snackbarOpen, setSnackbarOpen] = useState(false);

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
    if (!formData.purpose.trim()) {
      errors.purpose = "Purpose is required";
    } else if (formData.purpose.trim().length < 10) {
      errors.purpose = "Purpose must be at least 10 characters";
    }
    if (!formData.dataDescription.trim()) {
      errors.dataDescription = "Data description is required";
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
      dataDescription: "",
    });
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
          <strong>How it works:</strong> Enter the subject's tax ID and
          describe what data you need. The system will evaluate your request
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
                label="Data Description"
                value={formData.dataDescription}
                onChange={handleChange("dataDescription")}
                error={!!validationErrors.dataDescription}
                helperText={
                  validationErrors.dataDescription ||
                  "Describe what data you need to access"
                }
                margin="normal"
                required
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
                <Button variant="outlined" onClick={handleReset} disabled={loading}>
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
                  <CancelIcon sx={{ fontSize: 48, color: "error.main", mr: 2 }} />
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
                <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                  Reason
                </Typography>
                <Alert severity={isPermit ? "success" : "error"} icon={<InfoIcon />}>
                  {decision.reason}
                </Alert>
              </Box>

              {decision.policyGroupId && (
                <Box sx={{ mb: 2 }}>
                  <Typography variant="subtitle2" color="text.secondary" gutterBottom>
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
                  <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                    Required Actions
                  </Typography>
                  <Alert severity="warning" sx={{ mt: 1 }}>
                    <Typography variant="body2" gutterBottom>
                      You must fulfill these obligations:
                    </Typography>
                    <List dense>
                      {decision.obligations.map((obligation, index) => (
                        <ListItem key={index} sx={{ py: 0 }}>
                          <ListItemText
                            primary={
                              obligation.type.charAt(0).toUpperCase() +
                              obligation.type.slice(1)
                            }
                            secondary={obligation.detail}
                          />
                        </ListItem>
                      ))}
                    </List>
                  </Alert>
                </Box>
              )}

              {decision.blockchainTxHash && (
                <Box>
                  <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                    Blockchain Verification
                  </Typography>
                  <Paper variant="outlined" sx={{ p: 2, bgcolor: "grey.50" }}>
                    <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                      <Typography
                        variant="body2"
                        sx={{
                          fontFamily: "monospace",
                          wordBreak: "break-all",
                          flex: 1,
                        }}
                      >
                        {decision.blockchainTxHash}
                      </Typography>
                      <IconButton
                        size="small"
                        onClick={() => copyToClipboard(decision.blockchainTxHash)}
                      >
                        <CopyIcon fontSize="small" />
                      </IconButton>
                    </Box>
                    <Typography
                      variant="caption"
                      color="text.secondary"
                      sx={{ mt: 1, display: "block" }}
                    >
                      This decision has been recorded on the blockchain.
                    </Typography>
                  </Paper>
                </Box>
              )}

              {isPermit && (
                <Alert severity="info" sx={{ mt: 2 }}>
                  <Typography variant="body2">
                    Access granted. Ensure you fulfill all obligations listed above.
                  </Typography>
                </Alert>
              )}

              {!isPermit && (
                <Alert severity="warning" sx={{ mt: 2 }}>
                  <Typography variant="body2">
                    Access denied. Review the reason and adjust your request.
                  </Typography>
                </Alert>
              )}
            </Paper>
          ) : (
            <Card sx={{ bgcolor: "grey.50", height: "100%" }}>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Need Help?
                </Typography>
                <Typography variant="body2" paragraph>
                  <strong>Purpose examples:</strong>
                </Typography>
                <ul>
                  <li>
                    <Typography variant="body2">
                      Service Provision - fulfilling an order
                    </Typography>
                  </li>
                  <li>
                    <Typography variant="body2">
                      Research - academic or market research
                    </Typography>
                  </li>
                  <li>
                    <Typography variant="body2">
                      Marketing - promotional communications
                    </Typography>
                  </li>
                </ul>
                <Typography variant="body2" paragraph sx={{ mt: 2 }}>
                  <strong>Data description examples:</strong>
                </Typography>
                <ul>
                  <li>
                    <Typography variant="body2">
                      email from ecommerce database
                    </Typography>
                  </li>
                  <li>
                    <Typography variant="body2">purchase history</Typography>
                  </li>
                  <li>
                    <Typography variant="body2">contact information</Typography>
                  </li>
                </ul>
              </CardContent>
            </Card>
          )}
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