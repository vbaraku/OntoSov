import * as React from "react";
import { useContext, useState } from "react";
import { AuthContext } from "./AuthContext";
import { useNavigate } from "react-router-dom";
import axios from "axios";
import {
  Box,
  Button,
  CssBaseline,
  FormLabel,
  FormControl,
  Link,
  TextField,
  Typography,
  Stack,
  Card as MuiCard,
  RadioGroup,
  FormControlLabel,
  Radio,
  styled,
} from "@mui/material";

const Card = styled(MuiCard)(({ theme }) => ({
  display: "flex",
  flexDirection: "column",
  alignSelf: "center",
  width: "100%",
  padding: theme.spacing(4),
  gap: theme.spacing(2),
  margin: "auto",
  boxShadow:
    "hsla(220, 30%, 5%, 0.05) 0px 5px 15px 0px, hsla(220, 25%, 10%, 0.05) 0px 15px 35px -5px",
  [theme.breakpoints.up("sm")]: {
    width: "450px",
  },
  ...theme.applyStyles("dark", {
    boxShadow:
      "hsla(220, 30%, 5%, 0.5) 0px 5px 15px 0px, hsla(220, 25%, 10%, 0.08) 0px 15px 35px -5px",
  }),
}));

const SignUpContainer = styled(Stack)(({ theme }) => ({
  height: "calc((1 - var(--template-frame-height, 0)) * 100dvh)",
  minHeight: "100%",
  padding: theme.spacing(2),
  [theme.breakpoints.up("sm")]: {
    padding: theme.spacing(4),
  },
  content: '""',
  display: "block",
  position: "absolute",
  zIndex: -1,
  inset: 0,
  backgroundImage:
    "radial-gradient(ellipse at 50% 50%, hsl(210, 100%, 97%), hsl(0, 0%, 100%))",
  backgroundRepeat: "no-repeat",
  ...theme.applyStyles("dark", {
    backgroundImage:
      "radial-gradient(at 50% 50%, hsla(210, 100%, 16%, 0.5), hsl(220, 30%, 5%))",
  }),
}));

export default function SignUp() {
  const [role, setRole] = useState("SUBJECT");
  const [emailError, setEmailError] = useState(false);
  const [emailErrorMessage, setEmailErrorMessage] = useState("");
  const [passwordError, setPasswordError] = useState(false);
  const [passwordErrorMessage, setPasswordErrorMessage] = useState("");
  const [taxidError, setTaxidError] = useState(false);
  const [taxidErrorMessage, setTaxidErrorMessage] = useState("");
  const [nameError, setNameError] = useState(false);
  const [nameErrorMessage, setNameErrorMessage] = useState("");
  const { setUser } = useContext(AuthContext);
  const navigate = useNavigate();

  const validateInputs = () => {
    const email = document.getElementById("email");
    const password = document.getElementById("password");
    let isValid = true;

    if (!email.value || !/\S+@\S+\.\S+/.test(email.value)) {
      setEmailError(true);
      setEmailErrorMessage("Please enter a valid email address.");
      isValid = false;
    } else {
      setEmailError(false);
      setEmailErrorMessage("");
    }

    if (!password.value || password.value.length < 6) {
      setPasswordError(true);
      setPasswordErrorMessage("Password must be at least 6 characters long.");
      isValid = false;
    } else {
      setPasswordError(false);
      setPasswordErrorMessage("");
    }

    if (role === "SUBJECT") {
      const taxid = document.getElementById("taxid");
      if (!taxid.value || taxid.value.length < 1) {
        setTaxidError(true);
        setTaxidErrorMessage("Tax ID is required.");
        isValid = false;
      } else {
        setTaxidError(false);
        setTaxidErrorMessage("");
      }
    } else {
      const name = document.getElementById("name");
      if (!name.value || name.value.length < 1) {
        setNameError(true);
        setNameErrorMessage("Name is required.");
        isValid = false;
      } else {
        setNameError(false);
        setNameErrorMessage("");
      }
    }

    return isValid;
  };

  const handleSubmit = async (event) => {
    event.preventDefault();

    if (!validateInputs()) {
      return;
    }

    const form = event.currentTarget;
    const formData = {
      email: form.elements.email?.value,
      password: form.elements.password?.value,
      role: role,
      ...(role === "SUBJECT"
        ? { taxid: form.elements.taxid?.value }
        : { name: form.elements.name?.value }),
    };

    try {
      const response = await axios.post(
        "http://localhost:8080/auth/signup",
        formData
      );
      if (response.status === 200) {
        const user = response.data;
        setUser(user);
        sessionStorage.setItem("user", JSON.stringify(user));
        navigate(role === "SUBJECT" ? "/subject" : "/controller");
      }
    } catch (error) {
      console.error("Error during signup:", error);
    }
  };

  return (
    <>
      <CssBaseline enableColorScheme />
      <SignUpContainer
        direction="column"
        justifyContent="space-between"
        alignContent="center"
      >
        <Card variant="outlined">
          <Typography
            component="h1"
            variant="h4"
            sx={{ width: "100%", fontSize: "clamp(2rem, 10vw, 2.15rem)" }}
          >
            Sign up
          </Typography>
          <Box
            component="form"
            onSubmit={handleSubmit}
            sx={{ display: "flex", flexDirection: "column", gap: 2 }}
          >
            <FormControl>
              <FormLabel>Role</FormLabel>
              <RadioGroup
                row
                value={role}
                onChange={(e) => setRole(e.target.value)}
              >
                <FormControlLabel
                  value="SUBJECT"
                  control={<Radio />}
                  label="Subject"
                />
                <FormControlLabel
                  value="CONTROLLER"
                  control={<Radio />}
                  label="Controller"
                />
              </RadioGroup>
            </FormControl>

            {role === "SUBJECT" ? (
              <FormControl>
                <FormLabel htmlFor="taxid">Tax ID</FormLabel>
                <TextField
                  autoComplete="taxid"
                  name="taxid"
                  required
                  fullWidth
                  id="taxid"
                  placeholder="123456789"
                  error={taxidError}
                  helperText={taxidErrorMessage}
                  color={taxidError ? "error" : "primary"}
                />
              </FormControl>
            ) : (
              <FormControl>
                <FormLabel htmlFor="name">Name</FormLabel>
                <TextField
                  autoComplete="name"
                  name="name"
                  required
                  fullWidth
                  id="name"
                  placeholder="Organisation Name"
                  error={nameError}
                  helperText={nameErrorMessage}
                  color={nameError ? "error" : "primary"}
                />
              </FormControl>
            )}

            <FormControl>
              <FormLabel htmlFor="email">Email</FormLabel>
              <TextField
                required
                fullWidth
                id="email"
                placeholder="your@email.com"
                name="email"
                autoComplete="email"
                variant="outlined"
                error={emailError}
                helperText={emailErrorMessage}
                color={emailError ? "error" : "primary"}
              />
            </FormControl>

            <FormControl>
              <FormLabel htmlFor="password">Password</FormLabel>
              <TextField
                required
                fullWidth
                name="password"
                placeholder="••••••"
                type="password"
                id="password"
                autoComplete="new-password"
                variant="outlined"
                error={passwordError}
                helperText={passwordErrorMessage}
                color={passwordError ? "error" : "primary"}
              />
            </FormControl>

            <Button
              type="submit"
              fullWidth
              variant="contained"
              onClick={validateInputs}
            >
              Sign up
            </Button>

            <Typography sx={{ textAlign: "center" }}>
              Already have an account?{" "}
              <span>
                <Link
                  href="/signin"
                  variant="body2"
                  sx={{ alignSelf: "center" }}
                >
                  Sign in
                </Link>
              </span>
            </Typography>
          </Box>
        </Card>
      </SignUpContainer>
    </>
  );
}
