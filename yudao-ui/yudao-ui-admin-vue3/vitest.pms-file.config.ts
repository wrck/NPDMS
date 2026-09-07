import { resolve } from 'node:path'
import vue from '@vitejs/plugin-vue'
import { configDefaults, defineConfig } from 'vitest/config'
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
    // These suites use node:test; integration-code-regression runs them separately.
    exclude: [
      ...configDefaults.exclude,
      'tests/theme-init.test.mjs',
      'src/components/PmsLocationSelector/locationSelector.spec.ts',
      'src/views/pms/asset/location/location-contract.spec.ts',
      'src/views/pms/engineering/installation/installationForm.spec.ts',
      'src/views/pms/project/projects/index.spec.ts'
    ],
    environment: './vitest.pms-file.environment.ts',
    setupFiles: ['./vitest.pms-file.setup.ts']
  },
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  }
})
