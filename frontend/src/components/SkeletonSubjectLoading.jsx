import React from "react";
import {
  Box,
  Typography,
  Card,
  CardContent,
  Grid,
  CircularProgress,
  Skeleton,
} from "@mui/material";

const SkeletonSubjectLoading = () => {
  return (
    <Box
      sx={{
        width: "100%",
        minHeight: "100vh",
        backgroundImage:
          "radial-gradient(ellipse at 50% 50%, hsl(210, 100%, 97%), hsl(0, 0%, 100%))",
        margin: 0,
        padding: 0,
      }}
    >
      <Box sx={{ maxWidth: "1200px", margin: "0 auto", p: 3 }}>
        {/* Header Skeleton */}
        <Box sx={{ mb: 4, mt: 2 }}>
          <Grid container spacing={2} alignItems="center" sx={{ mb: 3 }}>
            <Grid item>
              <Skeleton variant="circular" width={64} height={64} />
            </Grid>
            <Grid item xs>
              <Skeleton variant="text" width="40%" height={40} sx={{ mb: 1 }} />
              <Skeleton variant="text" width="60%" height={24} />
            </Grid>
          </Grid>

          {/* Stats Cards Skeleton */}
          <Grid container spacing={2} sx={{ mb: 3 }}>
            <Grid item xs={12} md={4}>
              <Card sx={{ boxShadow: 2 }}>
                <CardContent>
                  <Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
                    <Skeleton variant="circular" width={48} height={48} />
                    <Box sx={{ flex: 1 }}>
                      <Skeleton variant="text" width="60%" height={20} />
                      <Skeleton variant="text" width="40%" height={32} />
                    </Box>
                  </Box>
                </CardContent>
              </Card>
            </Grid>
            <Grid item xs={12} md={4}>
              <Card sx={{ boxShadow: 2 }}>
                <CardContent>
                  <Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
                    <Skeleton variant="circular" width={48} height={48} />
                    <Box sx={{ flex: 1 }}>
                      <Skeleton variant="text" width="60%" height={20} />
                      <Skeleton variant="text" width="40%" height={32} />
                    </Box>
                  </Box>
                </CardContent>
              </Card>
            </Grid>
            <Grid item xs={12} md={4}>
              <Card sx={{ boxShadow: 2 }}>
                <CardContent>
                  <Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
                    <Skeleton variant="circular" width={48} height={48} />
                    <Box sx={{ flex: 1 }}>
                      <Skeleton variant="text" width="60%" height={20} />
                      <Skeleton variant="text" width="40%" height={32} />
                    </Box>
                  </Box>
                </CardContent>
              </Card>
            </Grid>
          </Grid>

          {/* Action Button Skeleton */}
          <Box sx={{ display: "flex", justifyContent: "flex-end", mb: 3 }}>
            <Skeleton variant="rounded" width={180} height={42} />
          </Box>
        </Box>

        {/* Tabs Skeleton */}
        <Box sx={{ borderBottom: 1, borderColor: "divider", mb: 2 }}>
          <Skeleton variant="text" width={300} height={48} />
        </Box>

        {/* Loading message with spinner */}
        <Box
          sx={{
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            gap: 2,
            py: 4,
          }}
        >
          <CircularProgress size={48} />
          <Typography variant="h6" color="text.secondary">
            Gathering your data from connected sources...
          </Typography>
          <Typography variant="body2" color="text.secondary">
            This may take a few moments
          </Typography>
        </Box>

        {/* Data Cards Skeleton */}
        <Grid container spacing={3}>
          <Grid item xs={12}>
            <Card sx={{ boxShadow: 2 }}>
              <CardContent>
                <Box
                  sx={{ display: "flex", alignItems: "center", gap: 2, mb: 2 }}
                >
                  <Skeleton variant="circular" width={28} height={28} />
                  <Skeleton variant="text" width="30%" height={32} />
                </Box>
                <Skeleton
                  variant="text"
                  width="50%"
                  height={24}
                  sx={{ mb: 2 }}
                />
                <Skeleton variant="rectangular" width="100%" height={200} />
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={12}>
            <Card sx={{ boxShadow: 2 }}>
              <CardContent>
                <Box
                  sx={{ display: "flex", alignItems: "center", gap: 2, mb: 2 }}
                >
                  <Skeleton variant="circular" width={28} height={28} />
                  <Skeleton variant="text" width="30%" height={32} />
                </Box>
                <Skeleton
                  variant="text"
                  width="50%"
                  height={24}
                  sx={{ mb: 2 }}
                />
                <Skeleton variant="rectangular" width="100%" height={200} />
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      </Box>
    </Box>
  );
};

export default SkeletonSubjectLoading;
