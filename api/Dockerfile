FROM openjdk:11

ENV IDC zeus
ENV MODULE bella-queue
ENV ENVTYPE test
ENV DEBUGPORT 9008
ENV JMXPORT 9009
ENV MATRIX_CODE_DIR /opt/bella-queue/htdocs
ENV MATRIX_APPLOGS_DIR /opt/bella-queue/applogs
ENV MATRIX_ACCESSLOGS_DIR /opt/bella-queue/logs
ENV MATRIX_LOGS_DIR /opt/bella-queue/logs
ENV MATRIX_CACHE_DIR /opt/bella-queue/cache
ENV MATRIX_PRIVDATA_DIR /opt/bella-queue/privdata

COPY release/ /opt/bella-queue/htdocs/
RUN chmod +x /opt/bella-queue/htdocs/bin/*.sh

EXPOSE 8080
WORKDIR /opt/bella-queue/htdocs
VOLUME ["/opt/bella-queue/applogs", "/opt/bella-queue/logs", "/opt/bella-queue/cache", "/opt/bella-queue/privdata"]
CMD ["/bin/bash", "-x", "/opt/bella-queue/htdocs/bin/run.sh"]
