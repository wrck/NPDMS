import eslint from '@eslint/js'
import eslintPluginVue from 'eslint-plugin-vue'
import vueTsEslintConfig from '@vue/eslint-config-typescript'

export default [
  { ignores: ['dist/**', 'coverage/**', '.tmp-mock/**'] },
  eslint.configs.recommended,
  ...eslintPluginVue.configs['flat/recommended'],
  ...vueTsEslintConfig(),
  {
    rules: {
      'vue/multi-word-component-names': 'off'
    }
  }
]
