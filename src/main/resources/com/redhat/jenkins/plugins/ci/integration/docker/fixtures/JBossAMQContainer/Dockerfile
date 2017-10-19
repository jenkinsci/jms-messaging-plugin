from java:7

ENV FUSE_VERSION 6.3.0.redhat-309
ENV FUSE_PUBLIC_OPENWIRE_PORT 61616

# Install fuse in the image.
COPY install.sh /opt/jboss/install.sh
RUN chmod 777 /opt/jboss/install.sh
RUN /opt/jboss/install.sh

EXPOSE 61616 8181

# lets default to the jboss-fuse dir so folks can more easily navigate to around the server install
WORKDIR /opt/jboss/jboss-a-mq
CMD /opt/jboss/jboss-a-mq/bin/amq
