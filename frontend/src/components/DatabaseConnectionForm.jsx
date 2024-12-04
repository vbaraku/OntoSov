import React, { useState, useEffect } from "react";
import {
  TextField,
  Button,
  Box,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Alert,
  Typography,
} from "@mui/material";

const DatabaseConnectionForm = ({ onSubmit, controllerId, initialData = null, isEditMode = false }) => {
  const [formData, setFormData] = useState({
    databaseType: "",
    host: "",
    port: "",
    databaseName: "",
    username: "",
    password: "",
  });
  const [testStatus, setTestStatus] = useState(isEditMode ? { type: "success", message: "Connection verified" } : null);

  // Initialize form with existing data if provided
  useEffect(() => {
    if (initialData) {
      setFormData({
        databaseType: initialData.databaseType || "",
        host: initialData.host || "",
        port: initialData.port || "",
        databaseName: initialData.databaseName || "",
        username: initialData.username || "",
        password: "", // Don't set the password from initialData
        id: initialData.id, // Preserve the database ID for updates
      });
    }
  }, [initialData]);

  const databaseTypes = [
    { value: "postgresql", label: "PostgreSQL", defaultPort: "5432" },
    { value: "mysql", label: "MySQL", defaultPort: "3306" },
    { value: "sqlserver", label: "SQL Server", defaultPort: "1433" },
    { value: "oracle", label: "Oracle", defaultPort: "1521" },
  ];

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => {
      if (name === "databaseType") {
        const defaultPort = databaseTypes.find(
          (db) => db.value === value
        )?.defaultPort;
        return { ...prev, [name]: value, port: defaultPort || prev.port };
      }
      return { ...prev, [name]: value };
    });
  };

  const getJdbcUrl = () => {
    const { databaseType, host, port, databaseName } = formData;
    switch (databaseType) {
      case "postgresql":
        return `jdbc:postgresql://${host}:${port}/${databaseName}`;
      case "mysql":
        return `jdbc:mysql://${host}:${port}/${databaseName}`;
      case "sqlserver":
        return `jdbc:sqlserver://${host}:${port};databaseName=${databaseName}`;
      case "oracle":
        return `jdbc:oracle:thin:@${host}:${port}:${databaseName}`;
      default:
        return "";
    }
  };

  const handleTestConnection = async () => {
    try {
      setTestStatus({ type: "info", message: "Testing connection..." });

      const configToTest = {
        ...formData,
        jdbcUrl: getJdbcUrl(),
        // In edit mode, use existing password if no new one is provided
        password: formData.password || (initialData?.password || '')
      };

      const response = await fetch(
        "http://localhost:8080/api/database/connect",
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify(configToTest),
        }
      );

      if (!response.ok) {
        throw new Error("Connection test failed");
      }

      setTestStatus({ type: "success", message: "Connection successful!" });
    } catch (error) {
      setTestStatus({
        type: "error",
        message: `Connection failed: ${error.message}`,
      });
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const config = {
      ...formData,
      jdbcUrl: getJdbcUrl(),
      // In edit mode, use existing password if no new one is provided
      password: formData.password || (initialData?.password || ''),
      // Preserve the ID in edit mode
      id: initialData?.id
    };

    try {
      const response = await fetch(
        `http://localhost:8080/api/database/save-config?controllerId=${controllerId}`,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify(config),
        }
      );

      if (!response.ok) {
        throw new Error("Failed to save database configuration");
      }

      onSubmit(config);
    } catch (error) {
      setTestStatus({
        type: "error",
        message: `Failed to save configuration: ${error.message}`,
      });
    }
  };

  return (
    <Box
      component="form"
      onSubmit={handleSubmit}
      sx={{ maxWidth: 600, mx: "auto" }}
    >
      <Typography variant="h6" gutterBottom>
        {isEditMode ? "Edit Database Connection" : "New Database Connection"}
      </Typography>

      <FormControl fullWidth margin="normal">
        <InputLabel>Database Type</InputLabel>
        <Select
          name="databaseType"
          value={formData.databaseType}
          onChange={handleChange}
          required
          label="Database Type"
        >
          {databaseTypes.map((db) => (
            <MenuItem key={db.value} value={db.value}>
              {db.label}
            </MenuItem>
          ))}
        </Select>
      </FormControl>

      <TextField
        fullWidth
        margin="normal"
        label="Host"
        name="host"
        value={formData.host}
        onChange={handleChange}
        required
      />

      <TextField
        fullWidth
        margin="normal"
        label="Port"
        name="port"
        value={formData.port}
        onChange={handleChange}
        required
      />

      <TextField
        fullWidth
        margin="normal"
        label="Database Name"
        name="databaseName"
        value={formData.databaseName}
        onChange={handleChange}
        required
      />

      <TextField
        fullWidth
        margin="normal"
        label="Username"
        name="username"
        value={formData.username}
        onChange={handleChange}
        required
      />

      <TextField
        fullWidth
        margin="normal"
        label={isEditMode ? "New Password (optional)" : "Password"}
        name="password"
        type="password"
        value={formData.password}
        onChange={handleChange}
        required={!isEditMode}
        helperText={isEditMode ? "Leave blank to keep existing password" : ""}
      />

      {formData.databaseType && (
        <Box sx={{ mt: 2, mb: 2 }}>
          <Typography variant="body2" color="textSecondary">
            JDBC URL: {getJdbcUrl()}
          </Typography>
        </Box>
      )}

      {testStatus && (
        <Alert severity={testStatus.type} sx={{ mt: 2, mb: 2 }}>
          {testStatus.message}
        </Alert>
      )}

      <Box sx={{ mt: 3, display: "flex", gap: 2 }}>
        <Button
          variant="outlined"
          onClick={handleTestConnection}
          disabled={!formData.databaseType}
        >
          Test Connection
        </Button>
        <Button
          type="submit"
          variant="contained"
          disabled={!isEditMode && testStatus?.type !== "success"}
        >
          {isEditMode ? "Next" : "Connect"}
        </Button>
      </Box>
    </Box>
  );
};

export default DatabaseConnectionForm;
