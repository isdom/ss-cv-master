package com.yulore.cvmaster.service;

import com.yulore.api.CVMasterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CVMasterServiceImpl implements CVMasterService {
    @Override
    public void updateCVAgentStatus(final String agentId, final int freeWorks) {
        log.info("updateCVAgentStatus: agent[{}] - freeWorks: {}", agentId, freeWorks);
    }
}
