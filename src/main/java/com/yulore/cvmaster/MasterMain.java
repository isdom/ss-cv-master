package com.yulore.cvmaster;


import com.yulore.api.CVMasterService;
import io.netty.util.concurrent.DefaultThreadFactory;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRemoteService;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
public class MasterMain {
    @PostConstruct
    public void start() {
        log.info("CosyVoice-Master: Init: redisson: {}", redisson.getConfig().useSingleServer().getDatabase());

        serviceExecutor = Executors.newFixedThreadPool(_service_master_executors, new DefaultThreadFactory("cvMasterExecutor"));

        final RRemoteService rs2 = redisson.getRemoteService(_service_master);
        rs2.register(CVMasterService.class, masterService, _service_master_executors, serviceExecutor);
    }

    @PreDestroy
    public void stop() {
        serviceExecutor.shutdownNow();

        log.info("CosyVoice-Master: shutdown");
    }

    @Value("${service.cv_master.name}")
    private String _service_master;

    @Value("${service.cv_master.executors}")
    private int _service_master_executors;

    @Autowired
    private RedissonClient redisson;

    @Autowired
    private CVMasterService masterService;

    private ExecutorService serviceExecutor;
}