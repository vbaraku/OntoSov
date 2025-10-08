import React, { createContext, useState, useEffect } from "react";
import axios from "axios";

export const AuthContext = createContext();

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [isInitialized, setIsInitialized] = useState(false);

  // Initialize user from storage on app load
  useEffect(() => {
    const storedUser = localStorage.getItem("user") || sessionStorage.getItem("user");
    if (storedUser) {
      try {
        setUser(JSON.parse(storedUser));
      } catch (error) {
        console.error("Failed to parse stored user:", error);
        localStorage.removeItem("user");
        sessionStorage.removeItem("user");
      }
    }
    setIsInitialized(true);
  }, []);

  const login = async (email, password, rememberMe = false) => {
    try {
      const response = await axios.post("http://localhost:8080/auth/login", {
        email,
        password,
      });

      if (response.status === 200) {
        const userData = response.data;
        setUser(userData);
        
        // Store based on rememberMe preference
        if (rememberMe) {
          localStorage.setItem("user", JSON.stringify(userData));
          sessionStorage.removeItem("user"); // Clear session storage
        } else {
          sessionStorage.setItem("user", JSON.stringify(userData));
          localStorage.removeItem("user"); // Clear local storage
        }
        
        return userData;
      }
    } catch (error) {
      console.error("Login error:", error);
      throw error;
    }
  };

  const register = async (email, password, additionalData = {}) => {
    try {
      const response = await axios.post("http://localhost:8080/auth/signup", {
        email,
        password,
        ...additionalData,
      });

      if (response.status === 200) {
        const userData = response.data;
        setUser(userData);
        // Default to session storage for new registrations
        sessionStorage.setItem("user", JSON.stringify(userData));
        return userData;
      }
    } catch (error) {
      console.error("Registration error:", error);
      throw error;
    }
  };

  const logout = () => {
    setUser(null);
    localStorage.removeItem("user");
    sessionStorage.removeItem("user");
  };

  // Prevent rendering children until initialization is complete
  if (!isInitialized) {
    return (
      <div style={{ 
        display: 'flex', 
        justifyContent: 'center', 
        alignItems: 'center', 
        height: '100vh' 
      }}>
        Initializing...
      </div>
    );
  }

  return (
    <AuthContext.Provider value={{ user, setUser, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
};