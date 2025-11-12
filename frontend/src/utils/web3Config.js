import Web3 from 'web3';

export const CONTRACT_ADDRESSES = {
  AccessLogger: '0x5FbDB2315678afecb367f032d93F642f64180aa3'
};

export const RPC_ENDPOINT = 'http://localhost:8545';

export const ACCESS_LOGGER_ABI = [
  {
    "inputs": [{"internalType": "uint256", "name": "index", "type": "uint256"}],
    "name": "getLog",
    "outputs": [
      {"internalType": "address", "name": "controller", "type": "address"},
      {"internalType": "address", "name": "subject", "type": "address"},
      {"internalType": "bytes32", "name": "purposeHash", "type": "bytes32"},
      {"internalType": "string", "name": "action", "type": "string"},
      {"internalType": "bool", "name": "permitted", "type": "bool"},
      {"internalType": "string", "name": "policyGroupId", "type": "string"},
      {"internalType": "uint256", "name": "policyVersion", "type": "uint256"},
      {"internalType": "uint256", "name": "timestamp", "type": "uint256"}
    ],
    "stateMutability": "view",
    "type": "function"
  }
];

export const getWeb3Instance = () => new Web3(RPC_ENDPOINT);
export const getAccessLoggerContract = (web3) =>
  new web3.eth.Contract(ACCESS_LOGGER_ABI, CONTRACT_ADDRESSES.AccessLogger);
