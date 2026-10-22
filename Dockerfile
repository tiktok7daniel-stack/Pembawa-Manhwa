# Playwright image ships Chromium + system libs, which "Browser mode" needs for JavaScript-heavy sites.
FROM mcr.microsoft.com/playwright:v1.56.1-noble
WORKDIR /app
COPY package*.json ./
RUN npm ci --omit=dev
COPY . .
ENV NODE_ENV=production PORT=3000 PLAYWRIGHT_BROWSERS_PATH=/ms-playwright
EXPOSE 3000
USER pwuser
CMD ["node", "server/index.js"]
