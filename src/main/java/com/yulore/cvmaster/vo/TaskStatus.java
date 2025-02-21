package com.yulore.cvmaster.vo;

import lombok.Builder;
import lombok.ToString;

@Builder
@ToString
public class TaskStatus {
    public String task_id;
    public String status;
}
