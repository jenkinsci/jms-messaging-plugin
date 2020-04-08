from fedora:25

RUN yum install -y fedmsg-relay

COPY relay.py /etc/fedmsg.d/relay.py
COPY ssl.py /etc/fedmsg.d/ssl.py
COPY endpoints.py /etc/fedmsg.d/endpoints.py
#RUN sed -i -e 's/@IP@//g' /etc/prosody/prosody.cfg.lua

COPY setup.sh /tmp/setup.sh
RUN chmod 777 /tmp/setup.sh
RUN /tmp/setup.sh

# install openssh server
RUN yum -y install openssh-server

# install openssh clients
RUN yum -y install openssh-clients

RUN yum -y install procps

# make ssh directories
RUN mkdir /var/run/sshd

# create host keys
RUN ssh-keygen -b 1024 -t rsa -f /etc/ssh/ssh_host_key -P ""
RUN ssh-keygen -b 1024 -t rsa -f /etc/ssh/ssh_host_rsa_key -P ""
RUN ssh-keygen -b 1024 -t dsa -f /etc/ssh/ssh_host_dsa_key -P ""

# tell ssh to not use ugly PAM
RUN sed -i 's/UsePAM\syes/UsePAM no/' /etc/ssh/sshd_config
RUN mkdir -p /var/run/sshd

RUN useradd fedmsg2 -d /home/fedmsg2 && \
    mkdir -p /home/fedmsg2/.ssh && \
    echo "fedmsg2:fedmsg2" | chpasswd


# adding public key to authorized keys
ADD unsafe.pub /home/fedmsg2/
RUN cat /home/fedmsg2/unsafe.pub >> /home/fedmsg2/.ssh/authorized_keys
RUN chmod 400 /home/fedmsg2/.ssh/authorized_keys
RUN chown fedmsg2:fedmsg2 /home/fedmsg2/.ssh/authorized_keys

EXPOSE 4001
EXPOSE 2003
EXPOSE 22

COPY entrypoint.sh /usr/bin/entrypoint.sh
ENTRYPOINT ["/bin/sh", "/usr/bin/entrypoint.sh"]
