import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// サーバーは 127.0.0.1 だけで待ち受けるので、localhost(IPv6 の ::1 になることがある)ではなく直接指定する。
// changeOrigin: false で、ブラウザが開いている localhost:5173 をそのままサーバーに伝える
// (Google ログインの戻り先が http://localhost:5173/login/oauth2/code/google になるように)
const backend = { target: 'http://127.0.0.1:8080', changeOrigin: false }

export default defineConfig({
  plugins: [react()],
  build: {
    // ビルドした画面は Spring Boot が配信する(src/main/resources/static は git に入れない)
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
  },
  server: {
    open: '/',
    proxy: {
      '/api': backend,
      '/media': backend,
      // Google ログインの開始と、Google から戻る URL
      '/oauth2': backend,
      '/login/oauth2': backend,
    },
  },
})
