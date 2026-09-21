import axios from 'axios'

export interface RuntimeConfig {
  authMode: 'oauth2' | 'local'
  oidcAuthority: string
  oidcClientId: string
  oidcScope: string
  apiBaseUrl?: string
  projectClaim?: string
  telnetEnabled: boolean
}

let runtimeConfigPromise: Promise<RuntimeConfig> | undefined

export function loadRuntimeConfig(): Promise<RuntimeConfig> {
  runtimeConfigPromise ??= axios
    .get<RuntimeConfig>('/api/v1/runtime-config', { timeout: 10_000 })
    .then(({ data }) => data)
  return runtimeConfigPromise
}
