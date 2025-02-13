package com.yulore.cvmaster.vo;

import lombok.Builder;

@Builder
public class QueryTaskStatusResponse {
    public TaskStatus[] statuses;
}
