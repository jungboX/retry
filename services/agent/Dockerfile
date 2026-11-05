FROM python:3.12-slim
WORKDIR /app
COPY services/agent/pyproject.toml ./pyproject.toml
COPY services/agent/src ./src
RUN pip install --no-cache-dir .
ENV PYTHONPATH=/app/src
EXPOSE 8000
CMD ["uvicorn", "retry_agent.main:app", "--host", "0.0.0.0", "--port", "8000"]
