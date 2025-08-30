FROM python:3.11-slim

# Set working directory
WORKDIR /app

# Install system dependencies if needed
RUN apt-get update && apt-get install -y \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements and install Python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy locust test file
COPY locustfile.py .

# Expose port for web UI
EXPOSE 8089

# Default command (can be overridden)
CMD ["locust", "-f", "locustfile.py", "--host=http://localhost:8080"]
