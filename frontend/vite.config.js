import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    host: true,
    port: 3000,
    watch: {
      usePolling: true,
      interval: 100,
    },
    proxy: {
      '/api': process.env.VITE_BACKEND_URL ?? 'http://localhost:8080',
      '/login': {
        target: process.env.VITE_BACKEND_URL ?? 'http://localhost:8080',
        bypass: (req) => {
          // Let Vite serve the React app for GET /login so React Router renders LoginPage.
          // Only POST /login (form submission) goes to Spring Security.
          if (req.method === 'GET') return '/index.html';
        },
      },
      '/logout': process.env.VITE_BACKEND_URL ?? 'http://localhost:8080',
    },
  },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
  },
});
