import { getWeb3Instance, getAccessLoggerContract } from '../utils/web3Config';

const idToAddress = (id) => '0x' + id.toString(16).padStart(40, '0');

// LocalStorage cache key prefix
const CACHE_KEY_PREFIX = 'bv-';

// Save verification result to localStorage
const saveVerificationToCache = (txHash, logIndex, result) => {
  try {
    const cacheKey = `${CACHE_KEY_PREFIX}${txHash}-${logIndex}`;
    const cacheValue = {
      v: result.verified,
      t: Math.floor(Date.now() / 1000), // timestamp in seconds
      e: result.error || null,
      d: result.details || null
    };
    localStorage.setItem(cacheKey, JSON.stringify(cacheValue));
  } catch (error) {
    console.warn('Failed to cache verification result:', error);
  }
};

// Load verification result from localStorage
const loadVerificationFromCache = (txHash, logIndex) => {
  try {
    const cacheKey = `${CACHE_KEY_PREFIX}${txHash}-${logIndex}`;
    const cached = localStorage.getItem(cacheKey);
    if (cached) {
      const parsed = JSON.parse(cached);
      return {
        verified: parsed.v,
        error: parsed.e,
        details: parsed.d,
        timestamp: parsed.t * 1000 // convert back to milliseconds
      };
    }
  } catch (error) {
    console.warn('Failed to load cached verification:', error);
  }
  return null;
};

export const verifySingleLog = async (log, skipCache = false) => {
  try {
    if (log.blockchainLogIndex === null || log.blockchainLogIndex === undefined) {
      return { verified: false, error: 'No blockchain log index', details: null };
    }

    // Check cache first unless skipCache is true
    if (!skipCache && log.blockchainTxHash) {
      const cached = loadVerificationFromCache(log.blockchainTxHash, log.blockchainLogIndex);
      if (cached) {
        return cached;
      }
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

    const result = {
      verified: allMatch,
      error: null,
      details: { comparison, logIndex: log.blockchainLogIndex }
    };

    // Save to cache
    if (log.blockchainTxHash) {
      saveVerificationToCache(log.blockchainTxHash, log.blockchainLogIndex, result);
    }

    return result;
  } catch (error) {
    const result = { verified: false, error: error.message, details: null };
    // Cache errors too (to avoid repeatedly querying failed verifications)
    if (log.blockchainTxHash) {
      saveVerificationToCache(log.blockchainTxHash, log.blockchainLogIndex, result);
    }
    return result;
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
    // Use eth_blockNumber instead of net_listening (Hardhat compatible)
    await web3.eth.getBlockNumber();
    return { connected: true };
  } catch (error) {
    return { connected: false, error: error.message };
  }
};

// Export for loading cached verifications on component mount
export const loadCachedVerification = loadVerificationFromCache;
