import { createRequire } from 'node:module'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

export const browserRoot = dirname(fileURLToPath(import.meta.url))
export const uiRoot = resolve(browserRoot, '../../../../../..')
// An existing installation may be read without modifying its lockfile or node_modules.
export const dependencyRoot = process.env.NPDMS_UI_DEPENDENCIES || uiRoot
export const dependencies = createRequire(resolve(dependencyRoot, 'package.json'))
const vuePlugin = (await import(pathToFileURL(dependencies.resolve('@vitejs/plugin-vue')).href))
  .default

export default {
  root: uiRoot,
  plugins: [vuePlugin()],
  cacheDir: resolve(uiRoot, '../../.run/delivery-demo/vite-cache'),
  optimizeDeps: { entries: [resolve(browserRoot, 'index.html')] },
  // This fixture uses plain CSS; do not load the application's PostCSS/tooling graph.
  css: { postcss: { plugins: [] } },
  resolve: {
    alias: [
      { find: '@', replacement: resolve(uiRoot, 'src') },
      { find: /^vue$/, replacement: dependencies.resolve('vue/dist/vue.runtime.esm-bundler.js') },
      {
        find: /^@form-create\/element-ui$/,
        replacement: dependencies.resolve('@form-create/element-ui')
      },
      {
        find: /^element-plus$/,
        replacement: resolve(
          dirname(dependencies.resolve('element-plus/package.json')),
          'es/index.mjs'
        )
      },
      {
        find: /^element-plus\/dist\/index.css$/,
        replacement: dependencies.resolve('element-plus/dist/index.css')
      },
      {
        find: /^vitest$/,
        replacement: resolve(dirname(dependencies.resolve('vitest/package.json')), 'dist/index.js')
      }
    ]
  },
  test: {
    environment: 'node',
    include: ['src/views/pms/delivery-business/requirement-analysis/demoTemplate.spec.ts']
  },
  server: { host: '127.0.0.1', fs: { allow: [uiRoot, dependencyRoot] } }
}
