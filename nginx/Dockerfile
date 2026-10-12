FROM nginx:1.27-alpine

# Install openssl for self-signed cert generation
RUN apk add --no-cache openssl

# Generate self-signed certificate
RUN mkdir -p /etc/nginx/certs && \
    openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
    -keyout /etc/nginx/certs/nginx-selfsigned.key \
    -out /etc/nginx/certs/nginx-selfsigned.crt \
    -subj "/C=US/ST=State/L=City/O=RoutineManager/CN=localhost"

COPY nginx.conf /etc/nginx/nginx.conf
EXPOSE 80 443
