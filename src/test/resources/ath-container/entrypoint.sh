#!/usr/bin/env bash

set -euf -o pipefail
set -x

# The gid of mapped socket might or might not collide with existing group. Add user to that group creating it if it does not exist.
socketOwnerGid="$(stat -c '%g' /var/run/docker.sock)"
if ! getent group "$socketOwnerGid"; then
    sudo groupadd -g "$socketOwnerGid" docker-mapped
fi
sudo usermod -a -G "$socketOwnerGid" ath-user
newgrp -

exec "$@"