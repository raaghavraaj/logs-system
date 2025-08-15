package com.resolveai.logsdistributor.services;

import com.resolveai.logsdistributor.models.LogPacket;
import org.springframework.stereotype.Service;

@Service
public class LogsDistributorServiceImpl implements LogsDistributorService {

    @Override
    public void distributePacket(LogPacket packet) {}
}
