import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
const fixture = fileURLToPath(new URL('.', import.meta.url))
const source = fileURLToPath(new URL('../../../src', import.meta.url))
const boundary = `${fixture}boundaries.ts`
export default defineConfig({
  root: fileURLToPath(new URL('../../..', import.meta.url)),
  plugins: [vue()],
  resolve: { alias: [
    { find: '@/utils/request', replacement: boundary },
    { find: '@/api/excel', replacement: boundary },
    { find: '@/stores/user', replacement: boundary },
    { find: '@/components/LowCodeComponentRegistry', replacement: boundary },
    { find: '@', replacement: source }
  ] },
  optimizeDeps: { noDiscovery: true, include: ['vue', 'vue-router', 'element-plus', '@form-create/element-ui'] },
  server: { host: '127.0.0.1', port: 4173, strictPort: true }
})
