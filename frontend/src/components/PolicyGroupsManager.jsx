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
  List,
  ListItem,
  ListItemText,
  ListItemSecondaryAction,
  Card,
  CircularProgress,
  Alert,
  Snackbar,
} from "@mui/material";
import { DatePicker } from "@mui/x-date-pickers/DatePicker";
import HelpOutlineIcon from "@mui/icons-material/HelpOutline";
import EditIcon from "@mui/icons-material/Edit";
import DeleteIcon from "@mui/icons-material/Delete";
import AddIcon from "@mui/icons-material/Add";

const tooltips = {
  read: "Basic access to view the data (e.g., viewing your profile information)",
  use: "Using data for operations (e.g., processing transactions, providing services)",
  share:
    "Sharing data with authorized third parties (e.g., sharing medical data with specialists)",
  aggregate: "Using data for anonymous statistics and analytics",
  modify: "Making updates or changes to the data",
  notification:
    "Receive notifications when your data is accessed according to allowed permissions",
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
          <TextField
            label="Purpose"
            value={formState.constraints.purpose}
            onChange={(e) =>
              setFormState((prev) => ({
                ...prev,
                constraints: {
                  ...prev.constraints,
                  purpose: e.target.value,
                },
              }))
            }
            placeholder="e.g., Marketing, Research, Service Provision"
          />
        </FormControl>

        <FormControl fullWidth sx={{ mb: 2 }}>
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

        <Box sx={{ display: "flex", alignItems: "center" }}>
          <FormControlLabel
            control={
              <Checkbox
                checked={formState.constraints.requiresNotification}
                onChange={(e) =>
                  setFormState((prev) => ({
                    ...prev,
                    constraints: {
                      ...prev.constraints,
                      requiresNotification: e.target.checked,
                    },
                  }))
                }
              />
            }
            label="Notify me when data is accessed"
          />
          <Tooltip title={tooltips.notification} arrow>
            <IconButton size="small">
              <HelpOutlineIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        </Box>

        <Box
          sx={{ mt: 3, display: "flex", justifyContent: "flex-end", gap: 2 }}
        >
          <Button onClick={onCancel}>Cancel</Button>
          <Button variant="contained" onClick={onSave}>
            {formState.id ? "Save Changes" : "Create Group"}
          </Button>
        </Box>
      </Box>
    );
  }
);

const AssignPolicyForm = React.memo(({ data, onSave, onCancel, groupName, initialSelectedData = {} }) => {
  const [selectedData, setSelectedData] = useState(initialSelectedData);

  const toggleDataSelection = (source, property) => {
    setSelectedData(prev => {
      const newState = {...prev};
      
      // Initialize if needed
      if (!newState[source]) {
        newState[source] = new Set([property]);
        return newState;
      }
      
      // Handle Set
      if (newState[source] instanceof Set) {
        if (newState[source].has(property)) {
          newState[source].delete(property);
          if (newState[source].size === 0) {
            delete newState[source];
          }
        } else {
          newState[source].add(property);
        }
        return newState;
      }
      
      // Handle Array
      if (Array.isArray(newState[source])) {
        if (newState[source].includes(property)) {
          newState[source] = newState[source].filter(p => p !== property);
          if (newState[source].length === 0) {
            delete newState[source];
          }
        } else {
          newState[source] = [...newState[source], property];
        }
        return newState;
      }
      
      // Handle Object
      if (typeof newState[source] === 'object') {
        if (newState[source][property]) {
          delete newState[source][property];
          if (Object.keys(newState[source]).length === 0) {
            delete newState[source];
          }
        } else {
          newState[source][property] = true;
        }
        return newState;
      }
      
      return newState;
    });
  };

  const isSelected = (source, property) => {
    if (!selectedData[source]) return false;
  
    // If it's a Set, use has method
    if (selectedData[source] instanceof Set) {
      return selectedData[source].has(property);
    }
    
    // If it's an Array, use includes
    if (Array.isArray(selectedData[source])) {
      return selectedData[source].includes(property);
    }
    
    // Otherwise, check if the property exists as a key
    return selectedData[source][property] === true;
  };

  const handleSave = () => {
    // Convert from any format to Array for the API
    const dataAssignments = {};
    
    Object.entries(selectedData).forEach(([source, value]) => {
      if (value instanceof Set) {
        dataAssignments[source] = Array.from(value);
      } else if (Array.isArray(value)) {
        dataAssignments[source] = [...value];
      } else if (typeof value === 'object') {
        dataAssignments[source] = Object.keys(value).filter(key => value[key]);
      }
    });
    
    onSave(dataAssignments);
  };

  return (
    <Box sx={{ p: 2 }}>
      {Object.entries(data).map(([source, properties]) => (
        <Card key={source} sx={{ p: 2, mb: 2 }}>
          <Typography variant="subtitle1" gutterBottom>
            {source}
          </Typography>
          {Object.entries(properties).map(([property, value]) => (
            <FormControlLabel
              key={property}
              control={
                <Checkbox
                  checked={isSelected(source, property)}
                  onChange={() => toggleDataSelection(source, property)}
                />
              }
              label={`${property}: ${value}`}
            />
          ))}
        </Card>
      ))}
      <Box sx={{ mt: 3, display: "flex", justifyContent: "flex-end", gap: 2 }}>
        <Button onClick={onCancel}>Cancel</Button>
        <Button
          variant="contained"
          onClick={handleSave}
          disabled={Object.keys(selectedData).length === 0}
        >
          Apply Policy Group
        </Button>
      </Box>
    </Box>
  );
});

const PolicyGroupsManager = ({ data, userId }) => {
  const [policyGroups, setPolicyGroups] = useState([]);
  const [selectedGroup, setSelectedGroup] = useState(null);
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [assignDialogOpen, setAssignDialogOpen] = useState(false);
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
    }
  }, [userId]);

  const fetchPolicyGroups = async () => {
    setLoading(true);
    try {
      const response = await fetch(
        `http://localhost:8080/api/policy-groups/${userId}`
      );
      if (!response.ok) throw new Error("Failed to fetch policy groups");
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

      if (!response.ok) throw new Error("Failed to create policy group");

      // Refresh groups list
      await fetchPolicyGroups();

      setSnackbar({
        open: true,
        message: "Policy group created successfully",
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

      if (!response.ok) throw new Error("Failed to update policy group");

      // Refresh groups list
      await fetchPolicyGroups();

      setSnackbar({
        open: true,
        message: "Policy group updated successfully",
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

      if (!response.ok) throw new Error("Failed to delete policy group");

      // Refresh groups list
      await fetchPolicyGroups();

      setSnackbar({
        open: true,
        message: "Policy group deleted successfully",
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
    if (cleanedId.includes('#')) {
      cleanedId = cleanedId.substring(cleanedId.lastIndexOf('#') + 1);
    } else if (cleanedId.includes('/')) {
      cleanedId = cleanedId.substring(cleanedId.lastIndexOf('/') + 1);
    }
    
    // Fetch current assignments for this policy group
    try {
      setLoading(true);
      const response = await fetch(`http://localhost:8080/api/policy-groups/${cleanedId}/assignments?subjectId=${userId}`);
      
      if (response.ok) {
        const currentAssignments = await response.json();
        setSelectedData(currentAssignments);
      }
    } catch (error) {
      console.error("Error fetching assignments:", error);
    } finally {
      setLoading(false);
      setAssignDialogOpen(true);
    }
  };

  const handleAssignData = async (dataAssignments) => {
    setLoading(true);
    try {
      // Extract the real ID from the policy#pg-... format
      const realGroupId = selectedGroup.id.includes("#")
        ? selectedGroup.id.split("#")[1]
        : selectedGroup.id;

      console.log(`Using cleaned ID: ${realGroupId}`);

      const response = await fetch(
        `http://localhost:8080/api/policy-groups/${realGroupId}/assign?subjectId=${userId}`,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            policyGroupId: realGroupId,
            dataAssignments,
          }),
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
        <Typography variant="h5">Policy Groups</Typography>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => {
            setFormState(initialFormState);
            setCreateDialogOpen(true);
          }}
        >
          Create Policy Group
        </Button>
      </Box>

      {policyGroups.length === 0 && !loading ? (
        <Alert severity="info">
          No policy groups found. Create your first policy group to manage data
          access.
        </Alert>
      ) : (
        <List>
          {policyGroups.map((group) => (
            <ListItem
              key={group.id}
              sx={{
                mb: 2,
                border: "1px solid",
                borderColor: "divider",
                borderRadius: 1,
              }}
            >
              <ListItemText
                primary={group.name}
                secondary={group.description}
              />
              <ListItemSecondaryAction>
                <IconButton onClick={() => handleEditGroup(group)}>
                  <EditIcon />
                </IconButton>
                <IconButton onClick={() => handleDeleteGroup(group.id)}>
                  <DeleteIcon />
                </IconButton>
                <Button
                  variant="outlined"
                  size="small"
                  onClick={() => handleAssignGroup(group)}
                  sx={{ ml: 1 }}
                >
                  Assign Data
                </Button>
              </ListItemSecondaryAction>
            </ListItem>
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
          {formState.id ? "Edit Policy Group" : "Create Policy Group"}
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
