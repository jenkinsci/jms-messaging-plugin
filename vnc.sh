#!/bin/bash

# $ eval $(./vnc.sh)

display=":42"
if [ ! -z "$1" ]; then
  display=":$1"
fi

vncserver -kill $display > /dev/null 2>&1
vncserver -geometry 1750x1250 $display > /dev/null 2>&1
if [ -x "$(which vncviewer)" ] ; then
    (vncviewer $display || vncviewer localhost$display) > /dev/null &
fi

echo export BROWSER_DISPLAY=$display
