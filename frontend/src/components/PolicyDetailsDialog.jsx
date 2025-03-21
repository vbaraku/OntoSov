import React from 'react';
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
  ListItemIcon
} from '@mui/material';
import { 
  Lock,
  CalendarToday,
  Description,
  Notifications
} from '@mui/icons-material';

// Helper function to format action name
const formatActionName = (action) => {
  return action.charAt(0).toUpperCase() + action.slice(1);
};

const PolicyDetailsDialog = ({ open, onClose, details }) => {
  if (!details) return null;

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="md"
      fullWidth
    >
      <DialogTitle>
        Data Protection Policies
      </DialogTitle>
      
      <DialogContent sx={{ pt: 1 }}>
        <Paper variant="outlined" sx={{ p: 2, mb: 3 }}>
          <Grid container spacing={2}>
            <Grid item xs={12} md={6}>
              <Typography variant="subtitle2" color="text.secondary">
                Data Property
              </Typography>
              <Typography variant="body1">
                {details.property}
              </Typography>
            </Grid>
            <Grid item xs={12} md={6}>
              <Typography variant="subtitle2" color="text.secondary">
                Data Source
              </Typography>
              <Typography variant="body1">
                {details.source}
              </Typography>
            </Grid>
          </Grid>
        </Paper>

        <Typography variant="h6" sx={{ mb: 2 }}>
          Applied Policy Groups
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
              {Array.isArray(policy.actions) ? (
                policy.actions.map(action => (
                  <Chip
                    key={action}
                    label={formatActionName(action)}
                    size="small"
                    color="primary"
                    variant="outlined"
                    sx={{ mr: 1, mb: 1 }}
                  />
                ))
              ) : (
                Object.keys(policy.actions).map(action => (
                  <Chip
                    key={action}
                    label={formatActionName(action)}
                    size="small"
                    color="primary"
                    variant="outlined"
                    sx={{ mr: 1, mb: 1 }}
                  />
                ))
              )}
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
                        primary={`Expires: ${new Date(policy.constraints.expiration).toLocaleDateString()}`}
                      />
                    </ListItem>
                  )}
                  
                  {policy.constraints.requiresNotification && (
                    <ListItem disablePadding>
                      <ListItemIcon sx={{ minWidth: 36 }}>
                        <Notifications fontSize="small" />
                      </ListItemIcon>
                      <ListItemText 
                        primary="Notifications enabled"
                      />
                    </ListItem>
                  )}
                </List>
              </Box>
            )}
            
            {index < details.policies.length - 1 && (
              <Divider sx={{ mt: 2 }} />
            )}
          </Paper>
        ))}
        
        <Box sx={{ display: 'flex', justifyContent: 'flex-end', mt: 2 }}>
          <Button 
            variant="outlined" 
            onClick={onClose}
          >
            Close
          </Button>
        </Box>
      </DialogContent>
    </Dialog>
  );
};

export default PolicyDetailsDialog;