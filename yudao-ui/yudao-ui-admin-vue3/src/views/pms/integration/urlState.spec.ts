import { afterEach, describe, expect, it, vi } from 'vitest'
import { replaceIntegrationUrlState } from './urlState'

describe('integration workspace URL state', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('preserves refresh state without starting a router navigation', () => {
    const replaceState = vi.fn()
    vi.stubGlobal('window', {
      location: {
        href: 'http://localhost/data-integration/workspace?tab=tasks&task=100#content'
      },
      history: { state: { position: 3, back: '/home' }, replaceState }
    })

    replaceIntegrationUrlState({ tab: 'runs', task: undefined, run: '200' })

    expect(replaceState).toHaveBeenCalledOnce()
    expect(replaceState).toHaveBeenCalledWith(
      {
        position: 3,
        back: '/home',
        current: '/data-integration/workspace?tab=runs&run=200#content'
      },
      '',
      '/data-integration/workspace?tab=runs&run=200#content'
    )
  })
})
