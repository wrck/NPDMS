import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
const fixture = fileURLToPath(new URL('.', import.meta.url))
export default defineConfig({
  root: fileURLToPath(new URL('../../..', import.meta.url)),
  plugins: [vue(), AutoImport({ imports: ['vue'], dts: false })],
  resolve: { alias: [
    { find: /^@\/router$/, replacement: `${fixture}router.ts` },
    { find: /^@\/stores\/user$/, replacement: `${fixture}boundaries.ts` },
    { find: /^@\/components\/LowCodeComponentRegistry$/, replacement: `${fixture}boundaries.ts` },
    { find: '@', replacement: fileURLToPath(new URL('../../../src', import.meta.url)) }
  ] },
  // The real authentication store imports CommonJS packages. Discover and prebundle
  // the fixture's complete dependency graph, just as the application dev server does.
  optimizeDeps: {
    entries: ['tests/lowcode/consumers/index.html'],
    include: ['vue', 'vue-router', 'element-plus', '@form-create/element-ui', 'web-storage-cache', 'jsencrypt']
  },
  server: { host: '127.0.0.1', port: 4174, strictPort: true }
})
