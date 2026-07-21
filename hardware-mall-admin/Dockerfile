# syntax=docker/dockerfile:1.6
FROM node:18-alpine AS builder
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm config set registry https://registry.npmmirror.com && npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /build/dist /usr/share/nginx/html
EXPOSE 80 443
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -qO- http://localhost/ || exit 1
