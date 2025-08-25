package com.resolveai.distributor.services;

import com.resolveai.distributor.models.LogPacket;

public interface DistributorService {
    void distributePacket(LogPacket packet);
}
