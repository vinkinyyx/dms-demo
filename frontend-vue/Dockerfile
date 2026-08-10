FROM node:20-alpine AS frontend-builder
WORKDIR /app
COPY frontend-vue/package*.json ./
RUN npm ci --registry=https://registry.npmmirror.com
COPY frontend-vue/ ./
RUN npm run build

FROM node:20-alpine AS admin-builder
WORKDIR /app
COPY admin-vue/package*.json ./
RUN npm ci --registry=https://registry.npmmirror.com
COPY admin-vue/ ./
RUN npm run build

FROM nginx:1.25-alpine
COPY --from=frontend-builder /app/dist /usr/share/nginx/html
COPY --from=admin-builder /app/dist /usr/share/nginx/html/admin
COPY frontend-vue/nginx-vue.conf /etc/nginx/nginx.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]