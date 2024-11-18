import React, { useState, useEffect } from 'react';
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
  TableRow
} from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import AddIcon from '@mui/icons-material/Add';

const SchemaMapper = ({ tables }) => {
  const [mappings, setMappings] = useState([]);
  const [selectedTable, setSelectedTable] = useState('');
  const [selectedColumn, setSelectedColumn] = useState('');
  const [selectedSchemaClass, setSelectedSchemaClass] = useState('');
  const [selectedSchemaProperty, setSelectedSchemaProperty] = useState('');

  // Schema.org common classes and properties (simplified for example)
  const schemaClasses = [
    'Person',
    'Product',
    'Order',
    'Organization',
    'Place'
  ];

  const schemaProperties = {
    Person: ['name', 'email', 'telephone', 'address'],
    Product: ['name', 'description', 'price', 'category'],
    Order: ['orderNumber', 'orderDate', 'price'],
    Organization: ['name', 'address', 'telephone'],
    Place: ['name', 'address', 'geo']
  };

  const handleAddMapping = () => {
    if (selectedTable && selectedColumn && selectedSchemaClass && selectedSchemaProperty) {
      const newMapping = {
        id: Date.now(),
        table: selectedTable,
        column: selectedColumn,
        schemaClass: selectedSchemaClass,
        schemaProperty: selectedSchemaProperty
      };
      setMappings([...mappings, newMapping]);
      // Reset selection
      setSelectedColumn('');
      setSelectedSchemaProperty('');
    }
  };

  const handleDeleteMapping = (mappingId) => {
    setMappings(mappings.filter(mapping => mapping.id !== mappingId));
  };

  const handleSaveMapping = () => {
    // Here you would typically send the mappings to your backend
    console.log('Saving mappings:', mappings);
  };

  return (
    <Box sx={{ maxWidth: 800, mx: 'auto' }}>
      <Typography variant="h6" gutterBottom>
        Map Database to Schema.org
      </Typography>

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
                {tables.map(table => (
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
                label="Column"
                value={selectedColumn}
                onChange={(e) => setSelectedColumn(e.target.value)}
                disabled={!selectedTable}
              >
                {selectedTable && tables
                  .find(t => t.name === selectedTable)
                  ?.columns.map(column => (
                    <MenuItem key={column} value={column}>
                      {column}
                    </MenuItem>
                  ))}
              </Select>
            </FormControl>
          </Grid>

          <Grid item xs={6}>
            <FormControl fullWidth>
              <InputLabel>Schema.org Class</InputLabel>
              <Select
                label="Schema.org Class"
                value={selectedSchemaClass}
                onChange={(e) => setSelectedSchemaClass(e.target.value)}
              >
                {schemaClasses.map(className => (
                  <MenuItem key={className} value={className}>
                    {className}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </Grid>

          <Grid item xs={6}>
            <FormControl fullWidth>
              <InputLabel>Schema.org Property</InputLabel>
              <Select
                label="Schema.org Property"
                value={selectedSchemaProperty}
                onChange={(e) => setSelectedSchemaProperty(e.target.value)}
                disabled={!selectedSchemaClass}
              >
                {selectedSchemaClass && schemaProperties[selectedSchemaClass]?.map(prop => (
                  <MenuItem key={prop} value={prop}>
                    {prop}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </Grid>

          <Grid item xs={12}>
            <Button
              variant="contained"
              startIcon={<AddIcon />}
              onClick={handleAddMapping}
              disabled={!selectedTable || !selectedColumn || !selectedSchemaClass || !selectedSchemaProperty}
            >
              Add Mapping
            </Button>
          </Grid>
        </Grid>
      </Paper>

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
                <TableCell>{mapping.table}</TableCell>
                <TableCell>{mapping.column}</TableCell>
                <TableCell>{mapping.schemaClass}</TableCell>
                <TableCell>{mapping.schemaProperty}</TableCell>
                <TableCell>
                  <IconButton
                    size="small"
                    onClick={() => handleDeleteMapping(mapping.id)}
                  >
                    <DeleteIcon />
                  </IconButton>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>

      <Box sx={{ mt: 3, display: 'flex', justifyContent: 'flex-end' }}>
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