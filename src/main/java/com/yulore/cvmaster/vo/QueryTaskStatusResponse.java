package com.yulore.cvmaster.vo;

import lombok.Builder;

@Builder
public class QueryTaskStatusResponse {
    public String task_id;
    public int status;
}
