# Open Liberty base image
FROM openliberty/open-liberty:kernel-java8-openj9-ubi

# Add config and app
COPY --chown=1001:0 liberty/wlp/usr/servers/defaultServer/server.xml /config/server.xml
COPY --chown=1001:0 liberty/wlp/usr/servers/defaultServer/bootstrap.properties /config/bootstrap.properties
COPY --chown=1001:0 ${project.artifactId}.war /config/apps/
COPY --chown=1001:0 ${project.artifactId}/WEB-INF/lib/redisson-3.16.3.jar /opt/ol/wlp/usr/shared/resources/
COPY --chown=1001:0 ${project.artifactId}/WEB-INF/lib/byte-buddy-1.10.14.jar /opt/ol/wlp/usr/shared/resources/
COPY --chown=1001:0 ${project.artifactId}/WEB-INF/lib/cache-api-1.0.0.jar /opt/ol/wlp/usr/shared/resources/
COPY --chown=1001:0 ${project.artifactId}/WEB-INF/lib/jackson-annotations-2.12.5.jar /opt/ol/wlp/usr/shared/resources/
COPY --chown=1001:0 ${project.artifactId}/WEB-INF/lib/jackson-core-2.12.5.jar /opt/ol/wlp/usr/shared/resources/
COPY --chown=1001:0 ${project.artifactId}/WEB-INF/lib/jackson-databind-2.12.5.jar /opt/ol/wlp/usr/shared/resources/
COPY --chown=1001:0 ${project.artifactId}/WEB-INF/lib/jackson-dataformat-yaml-2.12.5.jar /opt/ol/wlp/usr/shared/resources/
COPY --chown=1001:0 ${project.artifactId}/WEB-INF/lib/jboss-marshalling-2.0.11.Final.jar /opt/ol/wlp/usr/shared/resources/
COPY --chown=1001:0 ${project.artifactId}/WEB-INF/lib/jboss-marshalling-river-2.0.11.Final.jar /opt/ol/wlp/usr/shared/resources/
COPY --chown=1001:0 ${project.artifactId}/WEB-INF/lib/jodd-bean-5.1.6.jar /opt/ol/wlp/usr/shared/resources/
COPY --chown=1001:0 ${project.artifactId}/WEB-INF/lib/jodd-core-5.1.6.jar /opt/ol/wlp/usr/shared/resources/
COPY --chown=1001:0 ${project.artifactId}/WEB-INF/lib/netty-buffer-4.1.68.Final.jar /opt/ol/wlp/usr/shared/resources/
COPY --chown=1001:0 ${project.artifactId}/WEB-INF/lib/netty-codec-4.1.68.Final.jar /opt/ol/wlp/usr/shared/resources/
COPY --chown=1001:0 ${project.artifactId}/WEB-INF/lib/netty-codec-dns-4.1.68.Final.jar /opt/ol/wlp/usr/shared/resources/
COPY --chown=1001:0 ${project.artifactId}/WEB-INF/lib/netty-common-4.1.68.Final.jar /opt/ol/wlp/usr/shared/resources/
COPY --chown=1001:0 ${project.artifactId}/WEB-INF/lib/netty-handler-4.1.68.Final.jar /opt/ol/wlp/usr/shared/resources/
COPY --chown=1001:0 ${project.artifactId}/WEB-INF/lib/netty-resolver-4.1.68.Final.jar /opt/ol/wlp/usr/shared/resources/
COPY --chown=1001:0 ${project.artifactId}/WEB-INF/lib/netty-resolver-dns-4.1.68.Final.jar /opt/ol/wlp/usr/shared/resources/
COPY --chown=1001:0 ${project.artifactId}/WEB-INF/lib/netty-transport-4.1.68.Final.jar /opt/ol/wlp/usr/shared/resources/
COPY --chown=1001:0 ${project.artifactId}/WEB-INF/lib/reactive-streams-1.0.3.jar /opt/ol/wlp/usr/shared/resources/
COPY --chown=1001:0 ${project.artifactId}/WEB-INF/lib/reactor-core-3.4.4.jar /opt/ol/wlp/usr/shared/resources/
COPY --chown=1001:0 ${project.artifactId}/WEB-INF/lib/rxjava-3.0.12.jar /opt/ol/wlp/usr/shared/resources/
COPY --chown=1001:0 ${project.artifactId}/WEB-INF/lib/slf4j-api-1.7.30.jar /opt/ol/wlp/usr/shared/resources/
COPY --chown=1001:0 ${project.artifactId}/WEB-INF/lib/snakeyaml-1.27.jar /opt/ol/wlp/usr/shared/resources/

# This script will add the requested XML snippets, grow image to be fit-for-purpose and apply interim fixes
RUN configure.sh
