FROM grafana/grafana:main
USER root
RUN apk upgrade --no-cache
USER grafana
COPY ./provisioning /etc/grafana/provisioning