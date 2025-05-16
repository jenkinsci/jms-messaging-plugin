FROM fedora:42

RUN dnf -y update
RUN dnf -y install util-linux erlang rabbitmq-server

RUN mkdir /etc/systemd/system/rabbitmq-server.service.d/
COPY override.conf /etc/systemd/system/rabbitmq-server.service.d/override.conf
COPY rabbitmq.conf /etc/rabbitmq/rabbitmq.conf

EXPOSE 5672

USER root

ENTRYPOINT ["/usr/sbin/rabbitmq-server"]
