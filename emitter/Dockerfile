FROM python:3.11-slim

WORKDIR /app

# Install dependencies
COPY requirements.txt .
RUN pip install -r requirements.txt

# Copy emitter script
COPY log_emitter.py .

# Make script executable
RUN chmod +x log_emitter.py

# Run the emitter
CMD ["python", "log_emitter.py"]
