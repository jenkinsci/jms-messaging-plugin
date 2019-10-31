FUSE_ARTIFACT_ID=jboss-a-mq

FUSE_DISTRO_URL=http://origin-repository.jboss.org/nexus/content/groups/ea/org/jboss/amq/${FUSE_ARTIFACT_ID}/${FUSE_VERSION}/${FUSE_ARTIFACT_ID}-${FUSE_VERSION}.zip

# Lets fail fast if any command in this script does succeed.
set -e

#
# Lets switch to the /opt/jboss dir
#
cd /opt/jboss

# Download and extract the distro
echo ${FUSE_DISTRO_URL}
curl --HEAD ${FUSE_DISTRO_URL}
curl -L -O ${FUSE_DISTRO_URL}
ls -l
jar -xvf ${FUSE_ARTIFACT_ID}-${FUSE_VERSION}.zip
rm ${FUSE_ARTIFACT_ID}-${FUSE_VERSION}.zip
mv jboss-a-mq-${FUSE_VERSION} jboss-a-mq
chmod a+x jboss-a-mq/bin/*
rm jboss-a-mq/bin/*.bat

# Lets remove some bits of the distro which just add extra weight in a docker image.
rm -rf jboss-a-mq/extras
rm -rf jboss-a-mq/quickstarts

#
# Move the bundle cache and tmp directories outside of the data dir so it's not persisted between container runs
#
mv jboss-a-mq/data/tmp jboss-a-mq/tmp
echo '
org.osgi.framework.storage=${karaf.base}/tmp/cache
'>> jboss-a-mq/etc/config.properties
sed -i -e 's/-Djava.io.tmpdir="$KARAF_DATA\/tmp"/-Djava.io.tmpdir="$KARAF_BASE\/tmp"/' jboss-a-mq/bin/karaf
sed -i -e 's/-Djava.io.tmpdir="$KARAF_DATA\/tmp"/-Djava.io.tmpdir="$KARAF_BASE\/tmp"/' jboss-a-mq/bin/client
sed -i -e 's/-Djava.io.tmpdir="$KARAF_DATA\/tmp"/-Djava.io.tmpdir="$KARAF_BASE\/tmp"/' jboss-a-mq/bin/admin
sed -i -e 's/${karaf.data}\/generated-bundles/${karaf.base}\/tmp\/generated-bundles/' jboss-a-mq/etc/org.apache.felix.fileinstall-deploy.cfg

# lets remove the karaf.delay.console=true to disable the progress bar
sed -i -e 's/karaf.delay.console=true/karaf.delay.console=false/' jboss-a-mq/etc/config.properties
echo '
# Root logger
log4j.rootLogger=INFO, stdout, osgi:*VmLogAppender
log4j.throwableRenderer=org.apache.log4j.OsgiThrowableRenderer
# CONSOLE appender not used by default
log4j.appender.stdout=org.apache.log4j.ConsoleAppender
log4j.appender.stdout.layout=org.apache.log4j.PatternLayout
log4j.appender.stdout.layout.ConversionPattern=%d{ABSOLUTE} | %-5.5p | %-16.16t | %-32.32c{1} | %X{bundle.id} - %X{bundle.name} - %X{bundle.version} | %m%n
' > jboss-a-mq/etc/org.ops4j.pax.logging.cfg

echo '
bind.address=0.0.0.0
'>> jboss-a-mq/etc/system.properties
echo 'admin=redhat,admin' >> jboss-a-mq/etc/users.properties

rm /opt/jboss/install.sh

