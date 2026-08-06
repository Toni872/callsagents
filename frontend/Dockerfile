# Stage 1: build
FROM node:22-alpine AS build
WORKDIR /app

# Copiamos solo los manifests primero para cachear la instalación de deps
COPY package*.json ./

# Workaround: node:22-alpine viene con npm 10.9.x que tiene un bug con `npm ci`
# cuando el lockfile tiene muchas entries (genera ayuda en lugar de ejecutar).
# Upgrade a npm 12 antes de instalar para resolver.
RUN npm install -g npm@12.0.2 --silent && npm ci --no-audit --no-fund

# Ahora copiamos el resto del código y construimos
COPY . .
RUN npm run build

# Stage 2: serve
# Angular 17+ genera los assets en dist/<project>/browser/ (no en la raíz),
# así que copiamos el CONTENIDO de browser/ directamente al doc root de nginx.
FROM nginx:1.27-alpine
COPY --from=build /app/dist/callsagents-frontend/browser/ /usr/share/nginx/html/
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
