import { describe, expect, it } from 'vitest'
import { createRequest } from './use-request'

describe('request state fencing', () => {
  it('ignores late success and clears stale data on forbidden retry', async () => {
    const state = createRequest<string>()
    let finish!: (value: string) => void
    const old = state.run(() => new Promise<string>((resolve) => { finish = resolve }))
    await state.run(async () => 'new')
    finish('old'); await old
    expect(state.data.value).toBe('new')
    await state.run(async () => { throw { response: { status: 403 } } })
    expect(state.data.value).toBeUndefined()
    expect(state.error.value).toContain('403')
    expect(state.loading.value).toBe(false)
  })
})
