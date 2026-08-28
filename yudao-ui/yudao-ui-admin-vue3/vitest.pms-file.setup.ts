import { vi } from 'vitest'

vi.mock('@/hooks/web/useMessage', () => ({
  useMessage: () => ({
    confirm: vi.fn(async () => undefined),
    info: vi.fn(),
    success: vi.fn(),
    warning: vi.fn()
  })
}))
