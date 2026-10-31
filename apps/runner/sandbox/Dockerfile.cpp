# Imagen del sandbox de C++: compilador + el mismo arnés que Python (que compila una vez y ejecuta cada test).
# Se lanza con red desactivada y sin privilegios, igual que la de Python (ver executor.py).
FROM python:3.15-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends g++ \
    && rm -rf /var/lib/apt/lists/* \
    && python -m pip uninstall -y pip setuptools wheel 2>/dev/null || true

COPY harness.py /opt/harness.py
RUN chmod 0444 /opt/harness.py

USER 65534:65534
WORKDIR /tmp
ENV LANGUAGE_RUNTIME=cpp
ENTRYPOINT ["python", "-I", "/opt/harness.py"]
