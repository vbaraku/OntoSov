import React, { useContext, useRef, useEffect } from "react";
import { Navigate, Outlet } from "react-router-dom";
import { AuthContext } from "./AuthContext";

const ProtectedRoute = () => {
  const { user, setUser } = useContext(AuthContext);

  
  useEffect(() => {
    // Check localStorage/sessionStorage on mount
    const storedUser = localStorage.getItem("user") || sessionStorage.getItem("user");
    if (storedUser && !user) {
      setUser(JSON.parse(storedUser));
    }
  }, [setUser, user]);

  if (!user) {
    // Check if user data is available in local/session storage
    const storedUser = localStorage.getItem("user") || sessionStorage.getItem("user");
    if (storedUser) {
      setUser(JSON.parse(storedUser));
      return <Outlet />;
    } else {
      return <Navigate to="/signin" replace />;
    }
  }

  return <Outlet />;
};

export default ProtectedRoute;
