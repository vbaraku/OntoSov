import React, { useState, useEffect } from "react";
import {
  Box,
  Paper,
  Typography,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  TextField,
  Button,
  Grid,
  IconButton,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Autocomplete,
  CircularProgress,
  Alert,
} from "@mui/material";
import DeleteIcon from "@mui/icons-material/Delete";
import AddIcon from "@mui/icons-material/Add";

const SchemaMapper = ({ tables, dbConfig, controllerId, isEditMode = false }) => {
  const [mappings, setMappings] = useState([]);
  const [selectedTable, setSelectedTable] = useState("");
  const [selectedColumn, setSelectedColumn] = useState("");
  const [selectedSchemaClass, setSelectedSchemaClass] = useState(null);
  const [selectedSchemaProperty, setSelectedSchemaProperty] = useState(null);
  const [schemaClassOptions, setSchemaClassOptions] = useState([]);
  const [schemaPropertyOptions, setSchemaPropertyOptions] = useState([]);
  const [loading, setLoading] = useState(false);
  const [saveStatus, setSaveStatus] = useState(null);

  // Load existing mappings when in edit mode
  useEffect(() => {
    if (isEditMode && controllerId) {
      fetchExistingMappings();
    }
  }, [isEditMode, controllerId]);

  const fetchExistingMappings = async () => {
    try {
      setLoading(true);
      const response = await fetch(
        `http://localhost:8080/api/database/mappings/${controllerId}`
      );
      if (!response.ok) throw new Error("Failed to fetch existing mappings");
      const existingMappings = await response.json();
      setMappings(existingMappings.map((mapping, index) => ({
        ...mapping,
        id: index
      })));
    } catch (error) {
      console.error("Error fetching mappings:", error);
      setSaveStatus({ type: "error", message: error.message });
    } finally {
      setLoading(false);
    }
  };

  const getColumnsForTable = (tableName) => {
    const table = tables.find((t) => t.name === tableName);
    return table ? table.columns : [];
  };

  const searchSchemaClasses = async (query) => {
    try {
      const response = await fetch(
        `http://localhost:8080/api/schema/classes?query=${encodeURIComponent(query)}`
      );
      if (!response.ok) throw new Error("Failed to fetch schema classes");
      const data = await response.json();
      setSchemaClassOptions(data);
    } catch (error) {
      console.error("Error:", error);
      setSaveStatus({ type: "error", message: error.message });
    }
  };

  const fetchSchemaProperties = async (className) => {
    if (!className) return;
    try {
      setLoading(true);
      const response = await fetch(
        `http://localhost:8080/api/schema/properties/${encodeURIComponent(className)}`
      );
      if (!response.ok) throw new Error("Failed to fetch schema properties");
      const data = await response.json();
      setSchemaPropertyOptions(data);
    } catch (error) {
      console.error("Error:", error);
      setSaveStatus({ type: "error", message: error.message });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (selectedSchemaClass) {
      fetchSchemaProperties(selectedSchemaClass);
    }
  }, [selectedSchemaClass]);

  const handleAddMapping = () => {
    if (selectedTable && selectedColumn && selectedSchemaClass && selectedSchemaProperty) {
      const newMapping = {
        id: Date.now(),
        databaseTable: selectedTable,
        databaseColumn: selectedColumn,
        schemaClass: selectedSchemaClass,
        schemaProperty: selectedSchemaProperty,
      };
      setMappings([...mappings, newMapping]);
      setSelectedColumn("");
      setSelectedSchemaProperty(null);
      setSelectedSchemaClass(null);
    }
  };

  const handleSaveMapping = async () => {
    try {
      setSaveStatus({ type: "info", message: "Saving mappings..." });

      const mappingRequest = {
        databaseConfig: dbConfig,
        mappings: mappings.map((m) => ({
          databaseTable: m.databaseTable,
          databaseColumn: m.databaseColumn,
          schemaClass: m.schemaClass,
          schemaProperty: m.schemaProperty,
        })),
      };

      const response = await fetch(
        `http://localhost:8080/api/database/save-mappings?controllerId=${controllerId}`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(mappingRequest),
        }
      );

      if (!response.ok) throw new Error("Failed to save mappings");
      setSaveStatus({ type: "success", message: "Mappings saved successfully!" });
    } catch (error) {
      console.error("Error:", error);
      setSaveStatus({ type: "error", message: error.message });
    }
  };

  if (loading && mappings.length === 0) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '400px' }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box sx={{ maxWidth: 800, mx: "auto" }}>
      <Paper sx={{ p: 3, mb: 3 }}>
        <Grid container spacing={2}>
          <Grid item xs={6}>
            <FormControl fullWidth>
              <InputLabel>Table</InputLabel>
              <Select
                value={selectedTable}
                label="Table"
                onChange={(e) => setSelectedTable(e.target.value)}
              >
                {tables.map((table) => (
                  <MenuItem key={table.name} value={table.name}>
                    {table.name}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </Grid>

          <Grid item xs={6}>
            <FormControl fullWidth>
              <InputLabel>Column</InputLabel>
              <Select
                value={selectedColumn}
                label="Column"
                onChange={(e) => setSelectedColumn(e.target.value)}
                disabled={!selectedTable}
              >
                {getColumnsForTable(selectedTable).map((column) => (
                  <MenuItem key={column.name} value={column.name}>
                    {column.name} ({column.type})
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </Grid>

          <Grid item xs={6}>
            <Autocomplete
              value={selectedSchemaClass}
              onChange={(_, newValue) => setSelectedSchemaClass(newValue)}
              onInputChange={(_, newInputValue, reason) => {
                if (reason === 'input') {
                  searchSchemaClasses(newInputValue);
                }
              }}
              options={schemaClassOptions}
              renderInput={(params) => (
                <TextField
                  {...params}
                  label="Schema.org Class"
                />
              )}
              loading={loading}
            />
          </Grid>

          <Grid item xs={6}>
            <Autocomplete
              value={selectedSchemaProperty}
              onChange={(_, newValue) => setSelectedSchemaProperty(newValue)}
              options={schemaPropertyOptions}
              disabled={!selectedSchemaClass}
              renderInput={(params) => (
                <TextField {...params} label="Schema.org Property" />
              )}
            />
          </Grid>

          <Grid item xs={12}>
            <Button
              variant="contained"
              startIcon={<AddIcon />}
              onClick={handleAddMapping}
              disabled={
                !selectedTable ||
                !selectedColumn ||
                !selectedSchemaClass ||
                !selectedSchemaProperty
              }
            >
              Add Mapping
            </Button>
          </Grid>
        </Grid>
      </Paper>

      {saveStatus && (
        <Alert severity={saveStatus.type} sx={{ mb: 2 }}>
          {saveStatus.message}
        </Alert>
      )}

      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Database Table</TableCell>
              <TableCell>Database Column</TableCell>
              <TableCell>Schema.org Class</TableCell>
              <TableCell>Schema.org Property</TableCell>
              <TableCell>Action</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {mappings.map((mapping) => (
              <TableRow key={mapping.id}>
                <TableCell>{mapping.databaseTable}</TableCell>
                <TableCell>{mapping.databaseColumn}</TableCell>
                <TableCell>{mapping.schemaClass}</TableCell>
                <TableCell>{mapping.schemaProperty}</TableCell>
                <TableCell>
                  <IconButton
                    size="small"
                    onClick={() =>
                      setMappings(mappings.filter((m) => m.id !== mapping.id))
                    }
                  >
                    <DeleteIcon />
                  </IconButton>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>

      <Box sx={{ mt: 3, display: "flex", justifyContent: "flex-end" }}>
        <Button
          variant="contained"
          onClick={handleSaveMapping}
          disabled={mappings.length === 0}
        >
          Save Mappings
        </Button>
      </Box>
    </Box>
  );
};

export default SchemaMapper;