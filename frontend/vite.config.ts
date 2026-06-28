import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Escuchar en todas las interfaces (LAN) y aceptar el host tuneleado por cloudflared.
    host: true,
    allowedHosts: ['app.fil-grama.com'],
  },
})
