FROM fedora:30

RUN dnf install -y rabbitmq-server

RUN mkdir /etc/systemd/system/rabbitmq-server.service.d/
COPY override.conf /etc/systemd/system/rabbitmq-server.service.d/override.conf

EXPOSE 5672

ENTRYPOINT ["rabbitmq-server"]
