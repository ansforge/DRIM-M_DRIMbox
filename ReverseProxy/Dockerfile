FROM nginx:1.21.6-alpine

RUN set -ex \
 && rm -r /usr/share/nginx/html

COPY nginx/default.conf /etc/nginx/conf.d/
