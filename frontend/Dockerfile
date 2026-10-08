# syntax=docker/dockerfile:1
# Imagen del cliente web de PaqRap: compilación con Node y publicación con nginx. Contexto: frontend/.
# nginx sirve los estáticos y redirige /api y /ws al servicio «backend» (ver nginx.conf).

# 1) Compilación (Vite). VITE_DATA_SOURCE se fija al compilar: «server» usa el backend real.
FROM node:22-alpine AS compilacion
WORKDIR /fuente
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY . .
ARG VITE_DATA_SOURCE=server
ENV VITE_DATA_SOURCE=${VITE_DATA_SOURCE}
RUN npm run build

# 2) Publicación con nginx.
FROM nginx:1.28-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=compilacion /fuente/dist /usr/share/nginx/html
EXPOSE 80
