import React from "react";
import {
  Box,
  Typography,
  Dialog,
  DialogTitle,
  DialogContent,
  Button,
  Chip,
  Divider,
  Paper,
  Grid,
  List,
  ListItem,
  ListItemText,
  ListItemIcon,
} from "@mui/material";
import {
  Lock,
  CalendarToday,
  Description,
  Notifications,
  AttachMoney,
  Storage,
  Psychology,
} from "@mui/icons-material";

// Helper function to format action name
const formatActionName = (action) => {
  // Skip metadata actions that start with __
  if (action.startsWith("__")) {
    return null;
  }

  // Handle prohibition actions
  if (action.startsWith("prohibit-")) {
    const actionName = action.replace("prohibit-", "");
    // Handle the aiTraining special case
    if (actionName === "aiTraining") {
      return "Prohibited: AI Training";
    }
    return `Prohibited: ${
      actionName.charAt(0).toUpperCase() + actionName.slice(1)
    }`;
  }

  // Handle the aiTraining special case
  if (action === "aiTraining") {
    return "AI Training";
  }

  return action.charAt(0).toUpperCase() + action.slice(1);
};

const formatAlgorithmName = (algorithm) => {
  switch (algorithm) {
    case "federatedLearning":
      return "Federated Learning";
    case "differentialPrivacy":
      return "Differential Privacy";
    case "secureEnclave":
      return "Secure Enclave Processing";
    case "localProcessing":
      return "Local Processing Only";
    default:
      return algorithm; // Return as-is if not recognized
  }
};

const PolicyDetailsDialog = ({ open, onClose, details }) => {
  if (!details) return null;

  // Determine if this is an entity or property
  const isEntity = details.type === "entity";

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>Data Protection Policies</DialogTitle>

      <DialogContent sx={{ pt: 1 }}>
        <Paper variant="outlined" sx={{ p: 2, mb: 3 }}>
          <Grid container spacing={2}>
            <Grid item xs={12} md={6}>
              <Typography variant="subtitle2" color="text.secondary">
                {isEntity ? "Data Entity" : "Data Property"}
              </Typography>
              <Typography variant="body1">
                {isEntity ? `${details.entityType}` : details.property}
              </Typography>
            </Grid>
            <Grid item xs={12} md={6}>
              <Typography variant="subtitle2" color="text.secondary">
                Data Source
              </Typography>
              <Typography variant="body1">{details.source}</Typography>
            </Grid>
          </Grid>
        </Paper>

        <Typography variant="h6" sx={{ mb: 2 }}>
          Applied Policies
        </Typography>

        {details.policies.map((policy, index) => (
          <Paper key={policy.groupId} variant="outlined" sx={{ p: 2, mb: 2 }}>
            <Typography variant="subtitle1" color="primary" gutterBottom>
              {policy.groupName}
            </Typography>

            <Divider sx={{ my: 1 }} />

            <Typography variant="subtitle2" gutterBottom>
              Permitted Actions
            </Typography>

            <Box sx={{ mb: 2 }}>
              {Array.isArray(policy.actions)
                ? policy.actions
                    .filter((action) => !action.startsWith("__")) // Filter out metadata actions
                    .map((action) => {
                      const formattedAction = formatActionName(action);
                      return formattedAction ? (
                        <Chip
                          key={action}
                          label={formattedAction}
                          size="small"
                          color={
                            action.startsWith("prohibit-") ? "error" : "primary"
                          }
                          variant="outlined"
                          sx={{ mr: 1, mb: 1 }}
                        />
                      ) : null;
                    })
                : Object.keys(policy.actions)
                    .filter((action) => !action.startsWith("__"))
                    .map((action) => {
                      const formattedAction = formatActionName(action);
                      return formattedAction ? (
                        <Chip
                          key={action}
                          label={formattedAction}
                          size="small"
                          color={
                            action.startsWith("prohibit-") ? "error" : "primary"
                          }
                          variant="outlined"
                          sx={{ mr: 1, mb: 1 }}
                        />
                      ) : null;
                    })}
            </Box>

            {policy.constraints && (
              <Box>
                <Typography variant="subtitle2" gutterBottom>
                  Policy Constraints
                </Typography>
                <List dense disablePadding>
                  {policy.constraints.purpose && (
                    <ListItem disablePadding>
                      <ListItemIcon sx={{ minWidth: 36 }}>
                        <Description fontSize="small" />
                      </ListItemIcon>
                      <ListItemText
                        primary={`Purpose: ${policy.constraints.purpose}`}
                      />
                    </ListItem>
                  )}

                  {policy.constraints.expiration && (
                    <ListItem disablePadding>
                      <ListItemIcon sx={{ minWidth: 36 }}>
                        <CalendarToday fontSize="small" />
                      </ListItemIcon>
                      <ListItemText
                        primary={`Expires: ${new Date(
                          policy.constraints.expiration
                        ).toLocaleDateString()}`}
                      />
                    </ListItem>
                  )}
                </List>
              </Box>
            )}

            {/* Display consequences if present */}
            {policy.consequences && (
              <Box sx={{ mt: 2 }}>
                <Typography variant="subtitle2" gutterBottom>
                  Consequences for Violations
                </Typography>
                <List dense disablePadding>
                  {policy.consequences.notificationType && (
                    <ListItem disablePadding>
                      <ListItemIcon sx={{ minWidth: 36 }}>
                        <Notifications fontSize="small" />
                      </ListItemIcon>
                      <ListItemText
                        primary={`Notification via: ${policy.consequences.notificationType}`}
                      />
                    </ListItem>
                  )}

                  {policy.consequences.compensationAmount && (
                    <ListItem disablePadding>
                      <ListItemIcon sx={{ minWidth: 36 }}>
                        <AttachMoney fontSize="small" />
                      </ListItemIcon>
                      <ListItemText
                        primary={`Compensation: €${policy.consequences.compensationAmount}`}
                      />
                    </ListItem>
                  )}
                </List>
              </Box>
            )}

            {/* Display AI restrictions if present */}
            {policy.aiRestrictions && (
              <Box sx={{ mt: 2 }}>
                <Typography variant="subtitle2" gutterBottom>
                  AI Training Restrictions
                </Typography>
                <List dense disablePadding>
                  {policy.aiRestrictions.allowAiTraining === false ? (
                    <ListItem disablePadding>
                      <ListItemIcon sx={{ minWidth: 36 }}>
                        <Psychology fontSize="small" color="error" />
                      </ListItemIcon>
                      <ListItemText
                        primary="AI training prohibited"
                        primaryTypographyProps={{ color: "error" }}
                      />
                    </ListItem>
                  ) : policy.aiRestrictions.aiAlgorithm ? (
                    <ListItem disablePadding>
                      <ListItemIcon sx={{ minWidth: 36 }}>
                        <Psychology fontSize="small" />
                      </ListItemIcon>
                      <ListItemText
                        primary={`Allowed algorithm: ${formatAlgorithmName(
                          policy.aiRestrictions.aiAlgorithm
                        )}`}
                      />
                    </ListItem>
                  ) : (
                    <ListItem disablePadding>
                      <ListItemIcon sx={{ minWidth: 36 }}>
                        <Psychology fontSize="small" />
                      </ListItemIcon>
                      <ListItemText primary="AI training allowed with no restrictions" />
                    </ListItem>
                  )}
                </List>
              </Box>
            )}

            {index < details.policies.length - 1 && <Divider sx={{ mt: 2 }} />}
          </Paper>
        ))}

        <Box sx={{ display: "flex", justifyContent: "flex-end", mt: 2 }}>
          <Button variant="outlined" onClick={onClose}>
            Close
          </Button>
        </Box>
      </DialogContent>
    </Dialog>
  );
};

export default PolicyDetailsDialog;
