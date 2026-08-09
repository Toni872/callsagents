# Stage 1: build
FROM node:22-alpine AS build
WORKDIR /app

# Copy manifests first for caching
COPY package*.json ./

# Workaround: node:22-alpine ships with npm 10.9.x which has a bug with `npm ci`
# when the lockfile has many entries (shows help text instead of executing).
# Upgrade to npm 12 before installing.
RUN npm install -g npm@12.0.2 --silent && npm ci --no-audit --no-fund

# Copy rest and build
COPY . .
RUN npm run build

# Stage 2: serve
FROM nginx:1.27-alpine
COPY --from=build /app/dist/callsagents-frontend /usr/share/nginx/html
# nginx.conf uses ${BACKEND_HOST} / ${BACKEND_PORT} envsubst placeholders.
# envsubst (from gettext) replaces them at runtime with Railway's
# internal service hostname + port.
COPY nginx.conf /etc/nginx/conf.d/default.conf.template
RUN apk add --no-cache gettext
# Render the template into the final nginx config file at build time.
# We don't know the runtime hostname here (it depends on the Railway service
# name), so we substitute to a sensible default; the entrypoint re-substitutes
# with the actual env vars before launching nginx.
RUN envsubst '${BACKEND_HOST} ${BACKEND_PORT}' \
    < /etc/nginx/conf.d/default.conf.template > /etc/nginx/conf.d/default.conf

# Re-substitute at runtime to pick up the actual BACKEND_HOST / BACKEND_PORT
# (Railway injects them per service). The entrypoint overwrites the config
# with values from the live environment right before launching nginx.
CMD ["/bin/sh", "-c", "envsubst '${BACKEND_HOST} ${BACKEND_PORT}' < /etc/nginx/conf.d/default.conf.template > /etc/nginx/conf.d/default.conf && exec nginx -g 'daemon off;'"]

EXPOSE 80
