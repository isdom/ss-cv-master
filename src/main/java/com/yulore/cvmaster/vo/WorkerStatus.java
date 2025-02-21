package com.yulore.cvmaster.vo;

import lombok.Builder;
import lombok.ToString;

@Builder
@ToString
public class WorkerStatus {
    public int total_workers;
    public int free_workers;
}
