import React, { useContext, useEffect, useState } from "react";
import { Navigate, Outlet } from "react-router-dom";
import { AuthContext } from "./AuthContext";

const ProtectedRoute = () => {
  const { user, setUser } = useContext(AuthContext);
  const [isChecking, setIsChecking] = useState(true);

  useEffect(() => {
    // Only check storage once on mount
    const storedUser = localStorage.getItem("user") || sessionStorage.getItem("user");
    
    if (storedUser && !user) {
      try {
        setUser(JSON.parse(storedUser));
      } catch (error) {
        console.error("Failed to parse stored user:", error);
        // Clear invalid data
        localStorage.removeItem("user");
        sessionStorage.removeItem("user");
      }
    }
    
    setIsChecking(false);
  }, []); // Empty dependency array - only run once on mount

  // Show loading state while checking authentication
  if (isChecking) {
    return (
      <div style={{ 
        display: 'flex', 
        justifyContent: 'center', 
        alignItems: 'center', 
        height: '100vh' 
      }}>
        Loading...
      </div>
    );
  }

  // After checking, either show protected content or redirect
  return user ? <Outlet /> : <Navigate to="/signin" replace />;
};

export default ProtectedRoute;