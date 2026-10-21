import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {proxy: {
    '/api': {
      target: 'http://localhost:8080',
      headers: { 'X-Admin-Token': 'local-dev-token' },
    },
  },
  },
})



