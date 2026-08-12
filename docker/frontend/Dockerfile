# syntax=docker/dockerfile:1.7
FROM node:20.19.6-bookworm

WORKDIR /app
RUN corepack enable && corepack prepare pnpm@9.15.5 --activate
COPY yudao-ui/yudao-ui-admin-vue3/package.json yudao-ui/yudao-ui-admin-vue3/pnpm-lock.yaml ./
RUN --mount=type=cache,target=/pnpm/store \
    pnpm install --store-dir /pnpm/store --frozen-lockfile
COPY yudao-ui/yudao-ui-admin-vue3/ ./
COPY docker/frontend/env.docker ./.env.docker
RUN node ./node_modules/vite/bin/vite.js build --mode docker

EXPOSE 4173
CMD ["node", "./node_modules/vite/bin/vite.js", "preview", "--mode", "docker", "--host", "0.0.0.0", "--port", "4173"]
