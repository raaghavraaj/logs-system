from locust import HttpUser, task, between
import json
import random
import string
from datetime import datetime

class LogsDistributionUser(HttpUser):
    wait_time = between(0.1, 0.5)  # Wait 0.1-0.5 seconds between tasks
    
    def on_start(self):
        """Called when a user starts - check system health"""
        response = self.client.get("/api/v1/health")
        if response.status_code != 200:
            print(f"⚠️ Health check failed: {response.status_code}")
        else:
            print("✅ System health check passed")
    
    @task(8)  # Weight 8 - most common task
    def send_info_log_packet(self):
        """Send a log packet with INFO level messages"""
        packet = self.generate_log_packet(["INFO"], "info-test")
        self.send_log_packet(packet)
    
    @task(4)  # Weight 4 - moderate frequency  
    def send_mixed_log_packet(self):
        """Send a log packet with mixed log levels"""
        packet = self.generate_log_packet(["INFO", "WARN", "ERROR"], "mixed-test")
        self.send_log_packet(packet)
    
    @task(2)  # Weight 2 - less frequent
    def send_error_log_packet(self):
        """Send a log packet with ERROR level messages"""
        packet = self.generate_log_packet(["ERROR", "FATAL"], "error-test")
        self.send_log_packet(packet)
    
    @task(1)  # Weight 1 - least frequent
    def send_large_log_packet(self):
        """Send a large log packet with many messages"""
        packet = self.generate_large_log_packet()
        self.send_log_packet(packet)
    
    def generate_log_packet(self, levels, test_type, message_count=2):
        """Generate a realistic log packet"""
        messages = []
        for i in range(message_count):
            level = random.choice(levels)
            messages.append({
                "id": f"msg-{self.random_string(8)}",
                "level": level,
                "source": f"locust-{test_type}",
                "message": f"Locust {test_type} message {i+1} from user {self.user_id} - {level} level test"
            })
        
        return {
            "packetId": f"locust-{self.random_string(10)}",
            "agentId": f"locust-agent-{self.user_id}",
            "timestamp": datetime.utcnow().isoformat() + "Z",
            "messages": messages
        }
    
    def generate_large_log_packet(self):
        """Generate a large log packet with 5-10 messages"""
        levels = ["DEBUG", "INFO", "WARN", "ERROR", "FATAL"]
        message_count = random.randint(5, 10)
        return self.generate_log_packet(levels, "large-packet", message_count)
    
    def send_log_packet(self, packet):
        """Send log packet to distributor"""
        with self.client.post(
            "/api/v1/distribute",
            json=packet,
            headers={'Content-Type': 'application/json'},
            catch_response=True
        ) as response:
            if response.status_code == 202:
                response.success()
            else:
                response.failure(f"Expected 202, got {response.status_code}")
    
    @staticmethod
    def random_string(length):
        """Generate random string for IDs"""
        return ''.join(random.choices(string.ascii_lowercase + string.digits, k=length))
    
    @property 
    def user_id(self):
        """Get a consistent user ID for this instance"""
        if not hasattr(self, '_user_id'):
            self._user_id = f"user-{random.randint(1000, 9999)}"
        return self._user_id


class StressTestUser(HttpUser):
    """Specialized user class for stress testing"""
    wait_time = between(0.01, 0.1)  # Much faster requests
    
    @task
    def stress_test_rapid_fire(self):
        """Send rapid-fire requests for stress testing"""
        packet = {
            "packetId": f"stress-{self.random_string(8)}",
            "agentId": f"stress-agent-{random.randint(1, 100)}",
            "timestamp": datetime.utcnow().isoformat() + "Z",
            "messages": [
                {
                    "id": f"stress-msg-{self.random_string(6)}",
                    "level": "WARN",
                    "source": "stress-test",
                    "message": "High-frequency stress test message - testing system limits"
                }
            ]
        }
        
        with self.client.post(
            "/api/v1/distribute",
            json=packet,
            catch_response=True
        ) as response:
            if response.status_code != 202:
                response.failure(f"Stress test failed: {response.status_code}")
    
    @staticmethod
    def random_string(length):
        return ''.join(random.choices(string.ascii_lowercase + string.digits, k=length))
