import { resolve } from 'node:path'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [vue()],
  test: {
    environment: './vitest.pms-file.environment.ts'
  },
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  }
})
