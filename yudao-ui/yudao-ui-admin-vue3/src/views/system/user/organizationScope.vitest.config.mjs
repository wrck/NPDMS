import { fileURLToPath } from 'node:url'
import { resolve } from 'node:path'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import { defineConfig } from 'vitest/config'

const root = fileURLToPath(new URL('../../../..', import.meta.url))
export default defineConfig({
  plugins: [vue(), AutoImport({ imports: ['vue'], dts: false })],
  resolve: { alias: { '@': resolve(root, 'src') } },
  test: {
    environment: 'happy-dom',
    include: ['src/views/system/user/UserOrganizationScopeForm.spec.ts']
  }
})
