import type { Environment } from 'vitest/environments'

export default <Environment>{
  name: 'pms-file',
  viteEnvironment: 'client',
  setup() {
    return { teardown() {} }
  }
}
