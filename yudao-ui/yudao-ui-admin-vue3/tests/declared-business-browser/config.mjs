import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import vue from '@vitejs/plugin-vue'

export const fixtureRoot = dirname(fileURLToPath(import.meta.url))
export const uiRoot = resolve(fixtureRoot, '../..')
export default {
  root: uiRoot,
  plugins: [vue()],
  cacheDir: resolve(uiRoot, '../../.run/cloud-20261006/browser-vite-cache'),
  optimizeDeps: { entries: [resolve(fixtureRoot, 'index.html')] },
  css: { postcss: { plugins: [] } },
  resolve: { alias: [
    { find: '@/config/axios', replacement: resolve(fixtureRoot, 'request.ts') },
    { find: '@/utils/auth', replacement: resolve(fixtureRoot, 'auth.ts') },
    { find: '@/components/BusinessView/registry', replacement: resolve(fixtureRoot, 'nativeViews.ts') },
    { find: /^\.\/DeliveryPanel\.vue$/, replacement: resolve(fixtureRoot, 'InactivePanel.vue') },
    { find: '@', replacement: resolve(uiRoot, 'src') }
  ] },
  server: { host: '127.0.0.1', proxy: Object.fromEntries(
    ['/api/', '/admin-api/', '/fixture/', '/finish'].map(path => [path, 'http://127.0.0.1:27462'])
  ) }
}
