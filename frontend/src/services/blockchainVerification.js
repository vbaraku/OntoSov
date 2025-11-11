import { getWeb3Instance, getAccessLoggerContract } from '../utils/web3Config';

const idToAddress = (id) => '0x' + id.toString(16).padStart(40, '0');

export const verifySingleLog = async (log) => {
  try {
    if (log.blockchainLogIndex === null || log.blockchainLogIndex === undefined) {
      return { verified: false, error: 'No blockchain log index', details: null };
    }

    const web3 = getWeb3Instance();
    const contract = getAccessLoggerContract(web3);
    const blockchainData = await contract.methods.getLog(log.blockchainLogIndex).call();

    const blockchainLog = {
      controller: blockchainData[0],
      subject: blockchainData[1],
      action: blockchainData[3],
      permitted: blockchainData[4],
      policyGroupId: blockchainData[5]
    };

    const controllerAddress = idToAddress(log.controllerId);
    const subjectAddress = idToAddress(log.subjectId);
    const permitted = log.decision === 'PERMIT';

    const comparison = {
      controller: {
        system: controllerAddress,
        blockchain: blockchainLog.controller,
        match: controllerAddress.toLowerCase() === blockchainLog.controller.toLowerCase()
      },
      subject: {
        system: subjectAddress,
        blockchain: blockchainLog.subject,
        match: subjectAddress.toLowerCase() === blockchainLog.subject.toLowerCase()
      },
      action: {
        system: log.action,
        blockchain: blockchainLog.action,
        match: log.action === blockchainLog.action
      },
      permitted: {
        system: permitted,
        blockchain: blockchainLog.permitted,
        match: permitted === blockchainLog.permitted
      },
      policyGroupId: {
        system: log.policyGroupId || '',
        blockchain: blockchainLog.policyGroupId || '',
        match: (log.policyGroupId || '') === (blockchainLog.policyGroupId || '')
      }
    };

    const allMatch = Object.values(comparison).every(field => field.match);

    return {
      verified: allMatch,
      error: null,
      details: { comparison, logIndex: log.blockchainLogIndex }
    };
  } catch (error) {
    return { verified: false, error: error.message, details: null };
  }
};

export const verifyBatchLogs = async (logs) => {
  const results = { total: logs.length, verified: 0, failed: 0, details: [] };
  for (const log of logs) {
    const result = await verifySingleLog(log);
    results.details.push({ logId: log.id, ...result });
    if (result.verified) results.verified++;
    else results.failed++;
  }
  return results;
};

export const checkBlockchainConnection = async () => {
  try {
    const web3 = getWeb3Instance();
    const isConnected = await web3.eth.net.isListening();
    return isConnected ? { connected: true } : { connected: false, error: 'Cannot reach node' };
  } catch (error) {
    return { connected: false, error: error.message };
  }
};
