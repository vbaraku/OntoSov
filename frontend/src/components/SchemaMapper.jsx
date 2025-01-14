import React, { useState, useEffect } from "react";
import {
  Box,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
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
  Paper,
  Autocomplete,
  Switch,
  FormControlLabel,
  Alert,
} from "@mui/material";
import DeleteIcon from "@mui/icons-material/Delete";

const SchemaMapper = ({ tables, dbConfig, controllerId }) => {
  const [mappings, setMappings] = useState([]);
  const [selectedSourceTable, setSelectedSourceTable] = useState("");
  const [selectedSourceColumn, setSelectedSourceColumn] = useState("");
  const [selectedTargetTable, setSelectedTargetTable] = useState("");
  const [selectedTargetColumn, setSelectedTargetColumn] = useState("");
  const [isRelationshipMapping, setIsRelationshipMapping] = useState(false);
  const [selectedSchemaClass, setSelectedSchemaClass] = useState(null);
  const [selectedSchemaProperty, setSelectedSchemaProperty] = useState(null);
  const [schemaClassOptions, setSchemaClassOptions] = useState([]);
  const [schemaPropertyOptions, setSchemaPropertyOptions] = useState([]);
  const [saveStatus, setSaveStatus] = useState(null);

  const searchSchemaClasses = async (query) => {
    try {
      const response = await fetch(
        `http://localhost:8080/api/schema/classes?query=${encodeURIComponent(query)}`
      );
      const data = await response.json();
      setSchemaClassOptions(data);
    } catch (error) {
      console.error("Error fetching schema classes:", error);
    }
  };

  const fetchSchemaProperties = async (className) => {
    if (!className) return;
    try {
      const response = await fetch(
        `http://localhost:8080/api/schema/properties/${encodeURIComponent(className)}`
      );
      const data = await response.json();
      setSchemaPropertyOptions(data);
    } catch (error) {
      console.error("Error fetching schema properties:", error);
    }
  };

  useEffect(() => {
    if (selectedSchemaClass) {
      fetchSchemaProperties(selectedSchemaClass);
    }
  }, [selectedSchemaClass]);

  const getColumnsForTable = (tableName) => {
    const table = tables.find((t) => t.name === tableName);
    return table ? table.columns : [];
  };

  const handleAddMapping = () => {
    const newMapping = {
      id: Date.now(),
      sourceTable: selectedSourceTable,
      sourceColumn: selectedSourceColumn,
      targetTable: isRelationshipMapping ? selectedTargetTable : null,
      targetColumn: isRelationshipMapping ? selectedTargetColumn : null,
      schemaClass: selectedSchemaClass,
      schemaProperty: selectedSchemaProperty,
      isRelationship: isRelationshipMapping,
    };
    setMappings([...mappings, newMapping]);
    resetForm();
  };

  const resetForm = () => {
    setSelectedSourceTable("");
    setSelectedSourceColumn("");
    setSelectedTargetTable("");
    setSelectedTargetColumn("");
    setSelectedSchemaClass(null);
    setSelectedSchemaProperty(null);
  };

  const handleSaveMapping = async () => {
    try {
      setSaveStatus({ type: "info", message: "Saving mappings..." });

      const mappingRequest = {
        databaseConfig: dbConfig,
        mappings: mappings.map((m) => ({
          databaseTable: m.sourceTable,       // Using sourceTable instead of databaseTable
          databaseColumn: m.sourceColumn,     // Using sourceColumn instead of databaseColumn
          targetTable: m.targetTable,
          sourceKey: m.sourceColumn,          // For relationships, source column is the foreign key
          targetKey: m.targetColumn,          // Target column is usually the primary key
          schemaClass: m.schemaClass,
          schemaProperty: m.schemaProperty
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

  return (
    <Box sx={{ maxWidth: 800, mx: "auto" }}>
      <Paper sx={{ p: 3, mb: 3 }}>
        <Grid container spacing={2}>
          <Grid item xs={12}>
            <FormControlLabel
              control={
                <Switch
                  checked={isRelationshipMapping}
                  onChange={(e) => setIsRelationshipMapping(e.target.checked)}
                />
              }
              label="Create Relationship Mapping"
            />
          </Grid>

          <Grid item xs={6}>
            <FormControl fullWidth>
              <InputLabel>Source Table</InputLabel>
              <Select
                value={selectedSourceTable}
                label="Source Table"
                onChange={(e) => setSelectedSourceTable(e.target.value)}
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
              <InputLabel>Source Column</InputLabel>
              <Select
                value={selectedSourceColumn}
                label="Source Column"
                onChange={(e) => setSelectedSourceColumn(e.target.value)}
                disabled={!selectedSourceTable}
              >
                {getColumnsForTable(selectedSourceTable).map((column) => (
                  <MenuItem key={column.name} value={column.name}>
                    {column.name}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </Grid>

          {isRelationshipMapping && (
            <>
              <Grid item xs={6}>
                <FormControl fullWidth>
                  <InputLabel>Target Table</InputLabel>
                  <Select
                    value={selectedTargetTable}
                    label="Target Table"
                    onChange={(e) => setSelectedTargetTable(e.target.value)}
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
                  <InputLabel>Target Column</InputLabel>
                  <Select
                    value={selectedTargetColumn}
                    label="Target Column"
                    onChange={(e) => setSelectedTargetColumn(e.target.value)}
                    disabled={!selectedTargetTable}
                  >
                    {getColumnsForTable(selectedTargetTable).map((column) => (
                      <MenuItem key={column.name} value={column.name}>
                        {column.name}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Grid>
            </>
          )}

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
                <TextField {...params} label="Schema.org Class" />
              )}
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
              onClick={handleAddMapping}
              disabled={!selectedSourceTable || !selectedSourceColumn || !selectedSchemaClass || !selectedSchemaProperty}
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
              <TableCell>Source Table</TableCell>
              <TableCell>Source Column</TableCell>
              <TableCell>Target Table</TableCell>
              <TableCell>Target Column</TableCell>
              <TableCell>Schema Class</TableCell>
              <TableCell>Schema Property</TableCell>
              <TableCell>Type</TableCell>
              <TableCell>Action</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {mappings.map((mapping) => (
              <TableRow key={mapping.id}>
                <TableCell>{mapping.sourceTable}</TableCell>
                <TableCell>{mapping.sourceColumn}</TableCell>
                <TableCell>{mapping.targetTable || "-"}</TableCell>
                <TableCell>{mapping.targetColumn || "-"}</TableCell>
                <TableCell>{mapping.schemaClass}</TableCell>
                <TableCell>{mapping.schemaProperty}</TableCell>
                <TableCell>{mapping.isRelationship ? "Relationship" : "Property"}</TableCell>
                <TableCell>
                  <IconButton
                    onClick={() => setMappings(mappings.filter((m) => m.id !== mapping.id))}
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