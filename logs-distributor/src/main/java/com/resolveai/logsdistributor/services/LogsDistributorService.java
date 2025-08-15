package com.resolveai.logsdistributor.services;

import com.resolveai.logsdistributor.models.LogPacket;

public interface LogsDistributorService {
    void distributePacket(LogPacket packet);
}
