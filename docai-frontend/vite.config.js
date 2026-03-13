import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiTarget = env.VITE_DEV_API_TARGET || 'http://127.0.0.1:8080'

  return {
    plugins: [vue()],
    server: {
      proxy: {
        '/api': {
          target: apiTarget,
          changeOrigin: true,
        }
      }
    }
  }
})
