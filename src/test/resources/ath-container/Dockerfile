# Dockerfile to be used to run ATH itself.
#
# see docs/DOCKER.md for usage
#

# ubuntu and fedora does not seem to package ESR firefox. docker is in unstable repo in debian and installation breaks from time to time.
# centos has both working, note that it offers firefox ESR only
FROM centos:7
MAINTAINER ogondza@gmail.com

RUN yum -y update && \
    yum -y install \
        curl \
        docker \
        firefox \
        ImageMagick \
        iptables \
        maven \
        java-1.8.0-openjdk-devel \
        tigervnc-server \
        sudo \
        which

# Allow injecting uid and gid to match directory ownership
ARG uid=1000
ENV uid $uid
ARG gid=1000
ENV gid $gid

RUN groupadd ath-user -g $gid && \
    useradd ath-user -u $uid -g $gid -m -d /home/ath-user && \
    echo 'ath-user ALL=(ALL) NOPASSWD:ALL' >> /etc/sudoers && \
    sed -i 's/requiretty/!requiretty/g' /etc/sudoers

# TODO seems this can be picked up from the host, which is unwanted:
ENV XAUTHORITY /home/ath-user/.Xauthority

USER ath-user
RUN mkdir /home/ath-user/.vnc && (echo ath-user; echo ath-user) | vncpasswd /home/ath-user/.vnc/passwd
# Default content includes x-window-manager, which is not installed, plus other stuff we do not need (vncconfig, x-terminal-emulator, etc.):
RUN touch /home/ath-user/.vnc/xstartup && chmod a+x /home/ath-user/.vnc/xstartup

# Set SUID and SGID for docker binary so it can communicate with mapped socket its uid:gid we can not control. Alternative
# approach used for this is adding ath-user to the group of /var/run/docker.sock but that require root permission we do not
# have in ENTRYPOINT as the container is started as ath-user.
#USER root
#RUN chmod u+s "$(which docker)"

# Run interactive session by default
CMD [ "bash", "-c", "cd ~/ath && bash" ]
ADD entrypoint.sh entrypoint.sh
ENTRYPOINT [ "./entrypoint.sh" ]