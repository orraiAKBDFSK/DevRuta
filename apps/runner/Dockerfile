FROM python:3.13-slim

# Solo la CLI de Docker (binario estático); el servicio no necesita nada más.
COPY --from=docker:29-cli /usr/local/bin/docker /usr/local/bin/docker

WORKDIR /app
COPY plataforma_runner ./plataforma_runner
ENV PYTHONUNBUFFERED=1 PYTHONDONTWRITEBYTECODE=1

# Corre como root porque necesita hablar con el socket de Docker (ver docs/adr/0007).
# El código del estudiante NO corre aquí, sino en contenedores sandbox sin privilegios.
EXPOSE 8090
CMD ["python", "-m", "plataforma_runner.server"]
