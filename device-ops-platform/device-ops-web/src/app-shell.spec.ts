import { describe, expect, it } from 'vitest'

import { createIdempotencyKey, projectApiPath } from '@/api/device-ops'
import router from '@/router'
import {
  redactRequestSnapshot,
  scrubRequestCredentials
} from '@/security/transient-credentials'
import type { SubmitCollectionRequest } from '@/types/collection'

function request(): SubmitCollectionRequest {
  const project = {
    namespace: 'npdp',
    projectKey: 'PRJ-A/001',
    projectName: '核心网割接',
    projectCode: 'A-001'
  }
  return {
    namespace: project.namespace,
    project,
    targets: [
      {
        project,
        device: {
          deviceKey: 'router-01',
          deviceName: '核心路由器',
          vendor: 'Huawei',
          model: 'NE40E'
        },
        extensions: {},
        protocol: 'SSH2',
        host: '10.20.30.40',
        port: 22,
        username: 'ops',
        hostKeyFingerprint: 'SHA256:host-key-fingerprint',
        authenticationType: 'PASSWORD',
        executionMode: 'EXEC',
        connectTimeoutSeconds: 15,
        password: 'temporary-password'
      }
    ],
    script: {
      source: 'ADHOC_INLINE',
      key: 'display-version',
      version: 'request-1',
      content: 'display version',
      sha256: '0'.repeat(64),
      policy: 'EXECUTION_ONLY',
      parserType: 'NONE',
      parserConfig: ''
    },
    activityType: 'CUTOVER',
    commandTimeoutSeconds: 120,
    parseTimeoutSeconds: 15,
    leaseGraceSeconds: 10
  }
}

describe('standalone application shell', () => {
  it('preserves a string project key in the embedded route and API path', () => {
    const route = router.resolve('/embed/projects/PRJ-A%2F001')
    expect(route.name).toBe('embedded-project-collection')
    expect(route.params.projectKey).toBe('PRJ-A/001')
    expect(projectApiPath('PRJ-A/001', 'collections')).toBe(
      'projects/PRJ-A%2F001/collections'
    )
  })

  it('creates explicit unique idempotency keys', () => {
    expect(createIdempotencyKey()).not.toBe(createIdempotencyKey())
  })

  it('redacts evidence and clears transient credentials without using web storage', () => {
    const candidate = request()
    const localStorageSize = localStorage.length
    const sessionStorageSize = sessionStorage.length

    const snapshot = redactRequestSnapshot(candidate)
    expect(JSON.stringify(snapshot)).not.toContain('temporary-password')
    expect(JSON.stringify(snapshot)).toContain('display version')
    expect(candidate.targets[0]?.hostKeyFingerprint).toBe('SHA256:host-key-fingerprint')

    scrubRequestCredentials(candidate)
    expect(candidate.targets[0]?.password).toBe('')
    expect(localStorage.length).toBe(localStorageSize)
    expect(sessionStorage.length).toBe(sessionStorageSize)
  })
})
