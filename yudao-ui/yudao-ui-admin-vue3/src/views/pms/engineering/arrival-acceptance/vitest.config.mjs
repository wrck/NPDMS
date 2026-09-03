import { fileURLToPath } from 'node:url'
import { resolve } from 'node:path'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'
import AutoImport from 'unplugin-auto-import/vite'

const frontendRoot = fileURLToPath(new URL('../../../../..', import.meta.url))

export default defineConfig({
  plugins: [vue(), AutoImport({ imports: ['vue'], dts: false })],
  resolve: {
    alias: { '@': resolve(frontendRoot, 'src') }
  },
  test: {
    environment: resolve(
      frontendRoot,
      'src/views/pms/engineering/arrival-acceptance/arrivalTestEnvironment.mjs'
    )
  }
})
