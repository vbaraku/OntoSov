import React, { useState } from 'react';
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
  Card
} from '@mui/material';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import HelpOutlineIcon from '@mui/icons-material/HelpOutline';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import AddIcon from '@mui/icons-material/Add';

const tooltips = {
  read: "Basic access to view the data (e.g., viewing your profile information)",
  use: "Using data for operations (e.g., processing transactions, providing services)",
  share: "Sharing data with authorized third parties (e.g., sharing medical data with specialists)",
  aggregate: "Using data for anonymous statistics and analytics",
  modify: "Making updates or changes to the data",
  notification: "Receive notifications when your data is accessed according to allowed permissions"
};

const initialFormState = {
  name: '',
  description: '',
  permissions: {
    read: false,
    use: false,
    share: false,
    aggregate: false,
    modify: false
  },
  constraints: {
    purpose: '',
    expiration: null,
    requiresNotification: false
  }
};

const PolicyGroupForm = React.memo(({ formState, setFormState, onSave, onCancel }) => {
  const renderPermissionWithTooltip = (permission, label) => (
    <Box sx={{ display: 'flex', alignItems: 'center', mb: 1 }}>
      <FormControlLabel
        control={
          <Checkbox
            checked={formState.permissions[permission]}
            onChange={(e) => setFormState(prev => ({
              ...prev,
              permissions: {
                ...prev.permissions,
                [permission]: e.target.checked
              }
            }))}
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
        onChange={(e) => setFormState(prev => ({ ...prev, name: e.target.value }))}
        sx={{ mb: 2 }}
      />
      
      <TextField
        fullWidth
        label="Description"
        value={formState.description}
        onChange={(e) => setFormState(prev => ({ ...prev, description: e.target.value }))}
        multiline
        rows={2}
        sx={{ mb: 3 }}
      />

      <Typography variant="h6" gutterBottom>
        Permissions
      </Typography>
      {renderPermissionWithTooltip('read', 'Read')}
      {renderPermissionWithTooltip('use', 'Use')}
      {renderPermissionWithTooltip('share', 'Share')}
      {renderPermissionWithTooltip('aggregate', 'Aggregate')}
      {renderPermissionWithTooltip('modify', 'Modify')}

      <Typography variant="h6" gutterBottom sx={{ mt: 3 }}>
        Constraints
      </Typography>
      <FormControl fullWidth sx={{ mb: 2 }}>
        <TextField
          label="Purpose"
          value={formState.constraints.purpose}
          onChange={(e) => setFormState(prev => ({
            ...prev,
            constraints: {
              ...prev.constraints,
              purpose: e.target.value
            }
          }))}
          placeholder="e.g., Marketing, Research, Service Provision"
        />
      </FormControl>

      <FormControl fullWidth sx={{ mb: 2 }}>
        <DatePicker
          label="Expiration Date"
          value={formState.constraints.expiration}
          onChange={(date) => setFormState(prev => ({
            ...prev,
            constraints: {
              ...prev.constraints,
              expiration: date
            }
          }))}
          slotProps={{
            textField: {
              helperText: 'Leave empty for no expiration'
            }
          }}
        />
      </FormControl>

      <Box sx={{ display: 'flex', alignItems: 'center' }}>
        <FormControlLabel
          control={
            <Checkbox
              checked={formState.constraints.requiresNotification}
              onChange={(e) => setFormState(prev => ({
                ...prev,
                constraints: {
                  ...prev.constraints,
                  requiresNotification: e.target.checked
                }
              }))}
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

      <Box sx={{ mt: 3, display: 'flex', justifyContent: 'flex-end', gap: 2 }}>
        <Button onClick={onCancel}>Cancel</Button>
        <Button variant="contained" onClick={onSave}>
          {formState.id ? 'Save Changes' : 'Create Group'}
        </Button>
      </Box>
    </Box>
  );
});

const AssignPolicyForm = React.memo(({ data, onSave, onCancel, groupName }) => {
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
                  onChange={(e) => {
                    // Handle data selection
                  }}
                />
              }
              label={`${property}: ${value}`}
            />
          ))}
        </Card>
      ))}
      <Box sx={{ mt: 3, display: 'flex', justifyContent: 'flex-end', gap: 2 }}>
        <Button onClick={onCancel}>Cancel</Button>
        <Button variant="contained" onClick={onSave}>
          Apply Policy Group
        </Button>
      </Box>
    </Box>
  );
});

const PolicyGroupsManager = ({ data, onAssignPolicies }) => {
  const [policyGroups, setPolicyGroups] = useState([]);
  const [selectedGroup, setSelectedGroup] = useState(null);
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [assignDialogOpen, setAssignDialogOpen] = useState(false);
  const [formState, setFormState] = useState(initialFormState);

  const handleCreateGroup = () => {
    const newGroup = {
      ...formState,
      id: formState.id || Date.now()
    };

    if (formState.id) {
      setPolicyGroups(policyGroups.map(group => 
        group.id === formState.id ? newGroup : group
      ));
    } else {
      setPolicyGroups([...policyGroups, newGroup]);
    }
    
    setCreateDialogOpen(false);
    setFormState(initialFormState);
  };

  const handleDeleteGroup = (groupId) => {
    setPolicyGroups(policyGroups.filter(group => group.id !== groupId));
  };

  const handleEditGroup = (group) => {
    setFormState(group);
    setCreateDialogOpen(true);
  };

  const handleAssignGroup = (group) => {
    setSelectedGroup(group);
    setAssignDialogOpen(true);
  };

  return (
    <Box sx={{ width: '100%', maxWidth: 800, mx: 'auto', p: 3 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 3 }}>
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

      <List>
        {policyGroups.map((group) => (
          <ListItem
            key={group.id}
            sx={{
              mb: 2,
              border: '1px solid',
              borderColor: 'divider',
              borderRadius: 1
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
          {formState.id ? 'Edit Policy Group' : 'Create Policy Group'}
        </DialogTitle>
        <DialogContent>
          <PolicyGroupForm 
            formState={formState}
            setFormState={setFormState}
            onSave={handleCreateGroup}
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
            onSave={() => {
              // Handle assignment
              setAssignDialogOpen(false);
              setSelectedGroup(null);
            }}
            onCancel={() => {
              setAssignDialogOpen(false);
              setSelectedGroup(null);
            }}
          />
        </DialogContent>
      </Dialog>
    </Box>
  );
};

export default PolicyGroupsManager;