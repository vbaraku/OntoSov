import { buildModule } from "@nomicfoundation/hardhat-ignition/modules";

export default buildModule("ContractsModule", (m) => {
  // Deploy PolicyRegistry
  const policyRegistry = m.contract("PolicyRegistry");
  
  // Deploy AccessLogger
  const accessLogger = m.contract("AccessLogger");
  
  return { policyRegistry, accessLogger };
});