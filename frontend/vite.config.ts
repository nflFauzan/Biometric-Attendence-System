/// <reference types="vitest/config" />
import path from 'node:path'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    // Saat development, request /api dan /actuator diteruskan ke backend Spring Boot.
    proxy: {
      '/api': 'http://localhost:8080',
      '/actuator': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    // Lihat src/test/setup.ts: batas waktu dilonggarkan untuk mesin yang sibuk.
    testTimeout: 30_000,
    // index.css dibaca mentah (?raw) oleh uji kontras kaca; CSS lain tetap dilewati agar test cepat.
    css: { include: [/index\.css/] },
  },
})
