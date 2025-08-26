#!/usr/bin/env python3
"""
Log Emitter - Python script that generates and sends log packets to the distributor
"""

import json
import random
import time
import uuid
import os
import sys
from datetime import datetime, timezone
from typing import List, Dict, Any
import requests
from requests.exceptions import RequestException

class LogEmitter:
    def __init__(self, 
                 distributor_url: str = "http://localhost:8080/api/v1/logs",
                 agent_id: str = None,
                 emission_rate: float = 1.0,
                 burst_probability: float = 0.1):
        """
        Initialize the log emitter
        
        Args:
            distributor_url: URL of the distributor service
            agent_id: Unique identifier for this emitter agent
            emission_rate: Average packets per second
            burst_probability: Probability of generating burst traffic
        """
        self.distributor_url = distributor_url
        self.agent_id = agent_id or f"emitter-{uuid.uuid4().hex[:8]}"
        self.emission_rate = emission_rate
        self.burst_probability = burst_probability
        self.packets_sent = 0
        self.packets_failed = 0
        
        # Sample applications and services for realistic log generation
        self.applications = [
            {"app": "user-service", "services": ["authentication", "profile", "preferences"]},
            {"app": "payment-service", "services": ["billing", "transaction", "fraud-detection"]},
            {"app": "inventory-service", "services": ["stock", "warehouse", "shipping"]},
            {"app": "notification-service", "services": ["email", "sms", "push"]},
            {"app": "analytics-service", "services": ["tracking", "reporting", "insights"]}
        ]
        
        self.log_levels = ["DEBUG", "INFO", "WARN", "ERROR", "FATAL"]
        self.log_weights = [0.4, 0.35, 0.15, 0.08, 0.02]  # Realistic distribution
        
        print(f"🚀 Log Emitter initialized: agent_id={self.agent_id}, rate={emission_rate}pps")
    
    def generate_log_message(self, app_config: Dict[str, Any]) -> Dict[str, Any]:
        """Generate a realistic log message"""
        level = random.choices(self.log_levels, weights=self.log_weights)[0]
        service = random.choice(app_config["services"])
        instance = f"{service}-{random.randint(1, 3)}"
        host = f"prod-server-{random.randint(1, 10):02d}"
        
        # Generate realistic log messages based on level
        messages_by_level = {
            "DEBUG": [
                f"Executing {service} operation for user",
                f"Database query completed in {random.randint(1, 50)}ms",
                f"Cache hit for key: {service}_{random.randint(1000, 9999)}",
                f"Processing request with correlation-id: {uuid.uuid4().hex[:12]}"
            ],
            "INFO": [
                f"User successfully authenticated via {service}",
                f"Transaction completed successfully",
                f"{service.title()} service started on port {8080 + random.randint(1, 20)}",
                f"Processed {random.randint(1, 100)} items in batch"
            ],
            "WARN": [
                f"High response time detected in {service}: {random.randint(1000, 3000)}ms",
                f"Retry attempt {random.randint(1, 3)} for failed operation",
                f"Rate limit approaching for service {service}",
                f"Deprecated API endpoint accessed: /{service}/v1"
            ],
            "ERROR": [
                f"Failed to connect to {service} database",
                f"Authentication failed for user: invalid credentials",
                f"Service {service} timeout after {random.randint(5, 30)} seconds",
                f"Validation failed for {service} request payload"
            ],
            "FATAL": [
                f"Service {service} crashed due to out of memory error",
                f"Critical database connection pool exhausted",
                f"System {service} is unresponsive - requires immediate attention",
                f"Security breach detected in {service} module"
            ]
        }
        
        message = random.choice(messages_by_level[level])
        
        # Generate metadata based on service type
        metadata = {
            "correlationId": f"corr-{uuid.uuid4().hex[:12]}",
            "requestId": f"req-{uuid.uuid4().hex[:8]}",
            "sessionId": f"sess-{uuid.uuid4().hex[:10]}",
            "emitterAgent": self.agent_id
        }
        
        # Add service-specific metadata
        if "auth" in service:
            metadata.update({
                "userId": f"user-{random.randint(1000, 9999)}",
                "clientIp": f"192.168.{random.randint(1, 255)}.{random.randint(1, 255)}",
                "userAgent": "LogEmitter/1.0"
            })
        elif "payment" in service or "billing" in service:
            metadata.update({
                "amount": round(random.uniform(10.0, 1000.0), 2),
                "currency": random.choice(["USD", "EUR", "GBP"]),
                "transactionId": f"txn-{uuid.uuid4().hex[:12]}"
            })
        elif level in ["ERROR", "FATAL"]:
            metadata.update({
                "errorCode": f"{service.upper()}_{random.randint(1, 999):03d}",
                "stackTrace": f"at com.resolveai.{app_config['app']}.{service}.Service::{random.randint(50, 200)}"
            })
        
        return {
            "level": level,
            "source": {
                "application": app_config["app"],
                "service": service,
                "instance": instance,
                "host": host
            },
            "message": message,
            "metadata": metadata
        }
    
    def generate_log_packet(self, num_messages: int = None) -> Dict[str, Any]:
        """Generate a complete log packet with multiple messages"""
        if num_messages is None:
            # Realistic distribution of messages per packet
            num_messages = random.choices(
                [1, 2, 3, 4, 5, 8, 12, 20], 
                weights=[0.3, 0.25, 0.2, 0.1, 0.08, 0.04, 0.02, 0.01]
            )[0]
        
        app_config = random.choice(self.applications)
        messages = []
        
        for _ in range(num_messages):
            # 80% chance messages are from same app, 20% mixed
            if random.random() < 0.8:
                messages.append(self.generate_log_message(app_config))
            else:
                messages.append(self.generate_log_message(random.choice(self.applications)))
        
        packet = {
            "packetId": f"pkt-{uuid.uuid4()}",
            "agentId": self.agent_id,
            "totalMessages": num_messages,
            "messages": messages,
            "checksum": f"sha256:{uuid.uuid4().hex[:32]}"  # Simulated checksum
        }
        
        return packet
    
    def send_packet(self, packet: Dict[str, Any]) -> bool:
        """Send a log packet to the distributor"""
        try:
            response = requests.post(
                self.distributor_url,
                json=packet,
                headers={"Content-Type": "application/json"},
                timeout=10
            )
            
            if response.status_code == 202:
                self.packets_sent += 1
                print(f"📤 Sent packet {packet['packetId'][:12]}... "
                      f"({packet['totalMessages']} messages) - Total sent: {self.packets_sent}")
                return True
            else:
                print(f"❌ Failed to send packet {packet['packetId'][:12]}... "
                      f"HTTP {response.status_code}: {response.text}")
                self.packets_failed += 1
                return False
                
        except RequestException as e:
            print(f"❌ Network error sending packet {packet['packetId'][:12]}...: {e}")
            self.packets_failed += 1
            return False
    
    def run_continuous(self, duration: int = None):
        """Run the emitter continuously for a specified duration (or forever)"""
        print(f"🔄 Starting continuous emission at {self.emission_rate} packets/second")
        if duration:
            print(f"⏰ Will run for {duration} seconds")
        else:
            print("⏰ Running indefinitely (Ctrl+C to stop)")
        
        start_time = time.time()
        try:
            while True:
                # Check if duration limit reached
                if duration and (time.time() - start_time) > duration:
                    break
                
                # Determine if this is a burst
                is_burst = random.random() < self.burst_probability
                packets_to_send = random.randint(3, 8) if is_burst else 1
                
                if is_burst:
                    print(f"💥 Burst mode: sending {packets_to_send} packets")
                
                # Send packet(s)
                for _ in range(packets_to_send):
                    packet = self.generate_log_packet()
                    self.send_packet(packet)
                
                # Wait based on emission rate (adjusted for burst)
                delay = (1.0 / self.emission_rate) / packets_to_send
                time.sleep(max(0.1, delay))  # Minimum 0.1s delay
                
        except KeyboardInterrupt:
            print(f"\n🛑 Emitter stopped by user")
        finally:
            self.print_stats()
    
    def send_burst(self, num_packets: int, delay: float = 0.1):
        """Send a burst of packets"""
        print(f"💥 Sending burst of {num_packets} packets...")
        for i in range(num_packets):
            packet = self.generate_log_packet()
            success = self.send_packet(packet)
            if not success and i < num_packets - 1:  # Don't delay after last packet
                time.sleep(delay)
        self.print_stats()
    
    def print_stats(self):
        """Print emission statistics"""
        total = self.packets_sent + self.packets_failed
        success_rate = (self.packets_sent / total * 100) if total > 0 else 0
        print(f"\n📊 Emission Statistics:")
        print(f"   Packets sent: {self.packets_sent}")
        print(f"   Packets failed: {self.packets_failed}")
        print(f"   Success rate: {success_rate:.1f}%")

def main():
    """Main entry point"""
    # Configuration from environment variables
    distributor_url = os.getenv("DISTRIBUTOR_URL", "http://distributor:8080/api/v1/logs")
    agent_id = os.getenv("AGENT_ID", f"emitter-{os.getenv('HOSTNAME', 'local')}")
    emission_rate = float(os.getenv("EMISSION_RATE", "1.0"))
    mode = os.getenv("EMISSION_MODE", "continuous")  # continuous, burst, single
    duration = int(os.getenv("EMISSION_DURATION", "0")) or None  # 0 means infinite
    
    print(f"🎯 Configuration:")
    print(f"   Distributor URL: {distributor_url}")
    print(f"   Agent ID: {agent_id}")
    print(f"   Emission rate: {emission_rate} packets/second")
    print(f"   Mode: {mode}")
    print(f"   Duration: {duration or 'infinite'} seconds")
    
    burst_probability = float(os.getenv("BURST_PROBABILITY", "0.1"))
    
    emitter = LogEmitter(
        distributor_url=distributor_url,
        agent_id=agent_id,
        emission_rate=emission_rate,
        burst_probability=burst_probability
    )
    
    # Wait for services to be available
    max_retries = 30
    for attempt in range(max_retries):
        try:
            # Test connection to distributor
            response = requests.get(distributor_url.replace("/logs", "/health"), timeout=5)
            if response.status_code == 200:
                print(f"✅ Connected to distributor after {attempt + 1} attempts")
                break
        except RequestException:
            if attempt < max_retries - 1:
                print(f"⏳ Waiting for distributor... (attempt {attempt + 1}/{max_retries})")
                time.sleep(2)
            else:
                print(f"❌ Failed to connect to distributor after {max_retries} attempts")
                sys.exit(1)
    
    # Run based on mode
    if mode == "continuous":
        emitter.run_continuous(duration)
    elif mode == "burst":
        burst_size = int(os.getenv("BURST_SIZE", "10"))
        emitter.send_burst(burst_size)
    elif mode == "single":
        packet = emitter.generate_log_packet()
        emitter.send_packet(packet)
        emitter.print_stats()
    else:
        print(f"❌ Unknown emission mode: {mode}")
        sys.exit(1)

if __name__ == "__main__":
    main()
