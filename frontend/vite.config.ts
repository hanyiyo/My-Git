import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
export default defineConfig({ plugins: [vue()], server: { proxy: { '/api': { target: process.env.MEDICAL_API_URL ?? 'http://127.0.0.1:8080', changeOrigin: true } } } })
