import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import base, { dependencies, dependencyRoot, uiRoot } from '../../satisfaction/browser/config.mjs'
export { dependencies, dependencyRoot, uiRoot }
export const browserRoot = dirname(fileURLToPath(import.meta.url))
export default {
  ...base,
  cacheDir: resolve(uiRoot, '../../.run/delivery-demo/duration-cache'),
  optimizeDeps: { entries: [resolve(browserRoot, 'index.html')] },
  resolve: {
    ...base.resolve,
    alias: [
      { find: /^vue-router$/, replacement: dependencies.resolve('vue-router') },
      { find: /^@vueuse\/core$/, replacement: dependencies.resolve('@vueuse/core') },
      ...base.resolve.alias
    ]
  },
  test: {
    ...base.test,
    include: [
      'src/views/pms/project/project-master-detail/components/ProjectDurationContext.runtime.spec.ts',
      'src/views/pms/project/project-master-detail/components/ProjectDurationPanel.spec.ts'
    ]
  }
}
