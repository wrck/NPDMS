import { builtinEnvironments } from 'vitest/runtime'

export default {
  ...builtinEnvironments.node,
  name: 'cutover-node-web',
  viteEnvironment: 'client'
}
