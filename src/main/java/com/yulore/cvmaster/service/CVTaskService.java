package com.yulore.cvmaster.service;

import com.yulore.cvmaster.vo.CommitZeroShotTasksRequest;
import com.yulore.cvmaster.vo.CommitZeroShotTasksResponse;

public interface CVTaskService {
    CommitZeroShotTasksResponse CommitZeroShotTasks(final CommitZeroShotTasksRequest request);
}
