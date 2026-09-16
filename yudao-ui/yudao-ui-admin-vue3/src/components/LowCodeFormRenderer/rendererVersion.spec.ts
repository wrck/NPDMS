import { describe, expect, it } from 'vitest'
import type { FormConfig } from '@/api/lowcode'
import {
  LowCodeFormRendererVersion,
  normalizeLowCodeFormRendererVersion,
  resolveLowCodeFormRendererVersion,
  type VersionedFormConfig
} from './rendererVersion'

const config = (rendererVersion?: string | null): VersionedFormConfig => ({
  fields: [],
  ...(rendererVersion === undefined ? {} : { rendererVersion })
})

describe('LowCodeFormRenderer rendererVersion', () => {
  it('keeps legacy forms on V1 when rendererVersion is absent', () => {
    expect(resolveLowCodeFormRendererVersion(config())).toBe(LowCodeFormRendererVersion.V1)
  })

  it('selects V2 only when it is explicitly configured', () => {
    expect(resolveLowCodeFormRendererVersion(config('v2'))).toBe(LowCodeFormRendererVersion.V2)
    expect(resolveLowCodeFormRendererVersion(config('V2'))).toBe(LowCodeFormRendererVersion.V2)
  })

  it('falls back to V1 for null or unknown persisted values', () => {
    expect(resolveLowCodeFormRendererVersion(config(null))).toBe(LowCodeFormRendererVersion.V1)
    expect(resolveLowCodeFormRendererVersion(config('form-create'))).toBe(LowCodeFormRendererVersion.V1)
    expect(normalizeLowCodeFormRendererVersion(undefined)).toBe(LowCodeFormRendererVersion.V1)
  })

  it('allows an explicit consumer override without mutating persisted config', () => {
    const persisted = config('v2')
    expect(resolveLowCodeFormRendererVersion(persisted, 'v1')).toBe(LowCodeFormRendererVersion.V1)
    expect(resolveLowCodeFormRendererVersion(persisted, '')).toBe(LowCodeFormRendererVersion.V2)
    expect((persisted as FormConfig).fields).toEqual([])
  })
})
