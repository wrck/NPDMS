import { builtinEnvironments } from 'vitest/runtime'

export default {
  ...builtinEnvironments.node,
  name: 'arrival-node-web',
  viteEnvironment: 'client'
}
