import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          'vendor-vue': ['vue'],
          'vendor-markdown': ['marked', 'highlight.js'],
          'vendor-katex': ['katex'],
        }
      }
    }
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        configure: (proxy) => {
          proxy.on('proxyRes', (proxyRes, req, res) => {
            // Disable proxy buffering so SSE chunks reach the browser immediately
            delete proxyRes.headers['content-length'];
            res.setHeader('Cache-Control', 'no-cache, no-transform');
            res.setHeader('X-Accel-Buffering', 'no');
            res.flushHeaders();
          });
        }
      },
      '/share': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
