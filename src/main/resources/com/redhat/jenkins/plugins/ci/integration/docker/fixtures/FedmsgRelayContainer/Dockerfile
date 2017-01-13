from fedora:25

RUN yum install -y fedmsg-relay
COPY relay.py /etc/fedmsg.d/relay.py
COPY ssl.py /etc/fedmsg.d/ssl.py
COPY endpoints.py /etc/fedmsg.d/endpoints.py
#RUN sed -i -e 's/@IP@//g' /etc/prosody/prosody.cfg.lua

COPY setup.sh /tmp/setup.sh
RUN chmod 777 /tmp/setup.sh
RUN /tmp/setup.sh

EXPOSE 4001 2003

CMD /usr/bin/fedmsg-relay
