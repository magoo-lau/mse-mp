import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  // mse-ui-shared 以 file: 源码直出（.ts/.vue），排除出 dev 预构建交由插件链按源码处理
  optimizeDeps: {
    exclude: ['mse-ui-shared']
  },

  server: {
    port: 3500,
    // wujie 主应用跨域加载子应用资源，开发模式必须放开 CORS
    headers: {
      'Access-Control-Allow-Origin': '*'
    },
    proxy: {
      // 开发代理：直连 mp-boot 端口 8091（仅 /mp/** 业务端点；登录/系统管理在基座微服务，
      // 嵌入基座联调时基座 mse-ui 的 MSE_API_TARGET 指向网关 48080）
      '/api': {
        // target: 'http://localhost:8091',
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      }
    }
  }
})
