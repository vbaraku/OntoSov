import React, { useState } from "react";
import {
  Box,
  Card,
  CardContent,
  Typography,
  Button,
  Grid,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import EditIcon from "@mui/icons-material/Edit";
import DeleteIcon from "@mui/icons-material/Delete";
import StorageIcon from "@mui/icons-material/Storage";
import DatabaseConnectionForm from "../components/DatabaseConnectionForm";
import DatabaseMappingWizard from "../components/DatabaseMappingWizard";

const ControllerPage = () => {
  const [openNewDB, setOpenNewDB] = useState(false);
  const [openMapping, setOpenMapping] = useState(false);
  const [selectedDB, setSelectedDB] = useState(null);

  // Replace with API call later
  const [databases] = useState([
    {
      id: 1,
      name: "Customer Database",
      url: "jdbc:postgresql://localhost:5432/customers",
      mappings: ["Person", "Organization"],
    },
    {
      id: 2,
      name: "Product Database",
      url: "jdbc:postgresql://localhost:5432/products",
      mappings: ["Product", "Offer"],
    },
  ]);

  const handleEditMapping = (db) => {
    setSelectedDB(db);
    setOpenMapping(true);
  };

  return (
    <Box
      sx={{
        p: 3,
        backgroundImage:
          "radial-gradient(ellipse at 50% 50%, hsl(210, 100%, 97%), hsl(0, 0%, 100%))",
        minHeight: "100vh",
      }}
    >
      <Box sx={{ display: "flex", justifyContent: "space-between", mb: 3 }}>
        <Typography variant="h4">My Databases</Typography>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => setOpenNewDB(true)}
        >
          Connect New Database
        </Button>
      </Box>

      <Grid container spacing={3}>
        {databases.map((db) => (
          <Grid item xs={12} md={6} key={db.id}>
            <Card>
              <CardContent>
                <Box sx={{ display: "flex", alignItems: "center", mb: 2 }}>
                  <StorageIcon sx={{ mr: 1 }} />
                  <Typography variant="h6">{db.name}</Typography>
                </Box>
                <Typography color="textSecondary" gutterBottom>
                  {db.url}
                </Typography>
                <Typography variant="body2" sx={{ mb: 2 }}>
                  Mapped Schema.org Classes: {db.mappings.join(", ")}
                </Typography>
                <Box
                  sx={{ display: "flex", justifyContent: "flex-end", gap: 1 }}
                >
                  <IconButton
                    onClick={() => handleEditMapping(db)}
                    color="primary"
                  >
                    <EditIcon />
                  </IconButton>
                  <IconButton color="error">
                    <DeleteIcon />
                  </IconButton>
                </Box>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      <Dialog
        open={openNewDB}
        onClose={() => setOpenNewDB(false)}
        maxWidth="lg"
        fullWidth
      >
        <DialogTitle>Connect New Database</DialogTitle>
        <DatabaseMappingWizard onComplete={() => setOpenNewDB(false)} />
      </Dialog>

      <Dialog
        open={openMapping}
        onClose={() => setOpenMapping(false)}
        maxWidth="lg"
        fullWidth
      >
        <DialogTitle>Edit Database Mapping</DialogTitle>
        <DatabaseMappingWizard
          onComplete={() => setOpenMapping(false)}
          database={selectedDB}
        />
      </Dialog>
    </Box>
  );
};

export default ControllerPage;
