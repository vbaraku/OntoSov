import React, { useContext, useRef, useEffect } from "react";
import { Navigate, Outlet } from "react-router-dom";
import { AuthContext } from "./AuthContext";

const ProtectedRoute = () => {
  const { user, setUser } = useContext(AuthContext);

  
  useEffect(() => {
    // Check localStorage/sessionStorage on mount
    const storedUser = localStorage.getItem("user") || sessionStorage.getItem("user");
    if (storedUser && !user) {
      console.log("here")
      setUser(JSON.parse(storedUser));
    }
  }, []);

  if (!user) {
    return <Navigate to="/signin" replace />;
  }

  return <Outlet />;
};

export default ProtectedRoute;
