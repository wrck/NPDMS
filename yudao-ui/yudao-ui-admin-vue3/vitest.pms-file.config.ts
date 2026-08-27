import { resolve } from 'node:path'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'
import AutoImport from 'unplugin-auto-import/vite'

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      imports: [
        'vue',
        {
          '@/hooks/web/useMessage': ['useMessage']
        }
      ],
      dts: false
    })
  ],
  test: {
    environment: './vitest.pms-file.environment.ts',
    setupFiles: ['./vitest.pms-file.setup.ts']
  },
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  }
})
