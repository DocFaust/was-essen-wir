# Official Node and nginx images are published for linux/arm64 and linux/amd64.
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.31-alpine
RUN apk add --no-cache wget \
    && mkdir -p /var/cache/nginx /var/run/nginx \
    && chown -R nginx:nginx /var/cache/nginx /var/run/nginx /usr/share/nginx/html \
    && sed -i 's|pid\s\+/run/nginx.pid;|pid /var/run/nginx/nginx.pid;|' /etc/nginx/nginx.conf \
    && sed -i '/user\s\+nginx;/d' /etc/nginx/nginx.conf
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build --chown=nginx:nginx /app/dist /usr/share/nginx/html
USER nginx
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --retries=10 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/ || exit 1
