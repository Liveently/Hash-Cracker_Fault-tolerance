FROM nginx:latest

COPY ./ui /usr/share/nginx/html
COPY entrypoint.sh /entrypoint.sh

RUN chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
