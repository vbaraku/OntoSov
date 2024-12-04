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
import { useContext, useEffect } from "react";
import { AuthContext } from "../components/AuthContext";
import { useNavigate } from "react-router-dom";

const ControllerPage = () => {
  const { user } = useContext(AuthContext);
  const [openNewDB, setOpenNewDB] = useState(false);
  const [openMapping, setOpenMapping] = useState(false);
  const [selectedDB, setSelectedDB] = useState(null);
  const [databases, setDatabases] = useState([]);
  const [refreshKey, setRefreshKey] = useState(0); // Add refresh key for forcing updates

  const fetchDatabases = async () => {
    if (!user) return;
    
    console.log("Fetching databases...");
    try {
      const response = await fetch(
        `http://localhost:8080/api/database/controller/${user.id}/databases`
      );
      if (response.ok) {
        const data = await response.json();
        console.log("Received databases:", data);
        setDatabases(data);
      } else {
        console.error("Failed to fetch databases:", await response.text());
      }
    } catch (error) {
      console.error("Error fetching databases:", error);
    }
  };

  useEffect(() => {
    fetchDatabases();
  }, [user, refreshKey]);

  const handleEditMapping = (db) => {
    setSelectedDB(db);
    setOpenMapping(true);
  };

  const handleDeleteDatabase = async (dbId) => {
    try {
      const response = await fetch(
        `http://localhost:8080/api/database/controller/${user.id}/databases/${dbId}`,
        {
          method: 'DELETE'
        }
      );
      
      if (response.ok) {
        setDatabases(prevDatabases => prevDatabases.filter(db => db.id !== dbId));
      }
    } catch (error) {
      console.error("Error deleting database:", error);
    }
  };

  const handleNewDbComplete = async () => {
    console.log("New DB completion triggered");
    try {
      await fetchDatabases();
      setOpenNewDB(false);
      // Force a refresh
      setRefreshKey(prev => prev + 1);
    } catch (error) {
      console.error("Error in new DB completion:", error);
    }
  };
  
  const handleEditComplete = async () => {
    console.log("Edit completion triggered");
    try {
      await fetchDatabases();
      setOpenMapping(false);
      setSelectedDB(null);
      // Force a refresh
      setRefreshKey(prev => prev + 1);
    } catch (error) {
      console.error("Error in edit completion:", error);
    }
  };


  if (!user) {
    return <Box>Loading...</Box>;
  }

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
                  <Typography variant="h6">{db.databaseName}</Typography>
                </Box>
                <Typography color="textSecondary" gutterBottom>
                  {db.jdbcUrl}
                </Typography>
                <Typography variant="body2" sx={{ mb: 2 }}>
                  Type: {db.databaseType}
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
                  <IconButton 
                    color="error"
                    onClick={() => handleDeleteDatabase(db.id)}
                  >
                    <DeleteIcon />
                  </IconButton>
                </Box>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      {openNewDB && (
        <Dialog
          open={openNewDB}
          onClose={() => setOpenNewDB(false)}
          maxWidth="lg"
          fullWidth
        >
          <DialogTitle>Connect New Database</DialogTitle>
          <DialogContent>
            <DatabaseMappingWizard
              onComplete={handleNewDbComplete}
              controllerId={user.id}
            />
          </DialogContent>
        </Dialog>
      )}

      {openMapping && (
        <Dialog
          open={openMapping}
          onClose={() => setOpenMapping(false)}
          maxWidth="lg"
          fullWidth
        >
          <DialogTitle>Edit Database Mapping</DialogTitle>
          <DialogContent>
            <DatabaseMappingWizard
              onComplete={handleEditComplete}
              database={selectedDB}
              controllerId={user.id}
            />
          </DialogContent>
        </Dialog>
      )}
    </Box>
  );
};

export default ControllerPage;