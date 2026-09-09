import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import base, {
  dependencies,
  dependencyRoot,
  uiRoot
} from '../../requirement-analysis/browser/config.mjs'

export { dependencies, dependencyRoot, uiRoot }
export const browserRoot = dirname(fileURLToPath(import.meta.url))
const AutoImport = (
  await import(pathToFileURL(dependencies.resolve('unplugin-auto-import/vite')).href)
).default
export default {
  ...base,
  plugins: [
    ...base.plugins,
    AutoImport({ imports: ['vue', { '@/hooks/web/useMessage': ['useMessage'] }], dts: false })
  ],
  cacheDir: resolve(uiRoot, '../../.run/delivery-demo/satisfaction-cache'),
  optimizeDeps: { entries: [resolve(browserRoot, 'index.html')] },
  test: {
    environment: resolve(uiRoot, 'vitest.pms-file.environment.ts'),
    include: ['src/views/pms/project/satisfaction/projectContext.runtime.spec.ts']
  }
}
