import React, { useContext, useRef } from 'react';
import { Navigate, Outlet } from 'react-router-dom';
import { AuthContext } from './AuthContext';

const ProtectedRoute = () => {
  const { user } = useContext(AuthContext);

  // If the user is not logged in, redirect to the authentication page
  console.log("protected route")
  console.log(sessionStorage.getItem('user'))

  if (!user && !localStorage.getItem('user') && !sessionStorage.getItem('user')) {
    return <Navigate to="/signin" replace />;
  }

  // If the user is logged in, render the child components
  return <Outlet />;
};

export default ProtectedRoute;