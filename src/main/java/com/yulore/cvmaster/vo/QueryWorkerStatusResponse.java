package com.yulore.cvmaster.vo;

import lombok.Builder;

@Builder
public class QueryWorkerStatusResponse {
    public int total_workers;
    public int free_workers;
}
