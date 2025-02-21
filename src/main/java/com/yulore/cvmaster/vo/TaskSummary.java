package com.yulore.cvmaster.vo;

import lombok.Builder;
import lombok.ToString;

@Builder
@ToString
public class TaskSummary {
    public int pending;
    public int done;
}
