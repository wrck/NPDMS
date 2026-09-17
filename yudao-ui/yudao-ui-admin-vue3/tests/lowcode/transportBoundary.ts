import { vi } from 'vitest'

/**
 * Schema/renderer unit tests use the real lowcode API exports, not a duplicate
 * FieldType fixture. Only the HTTP/download boundaries are isolated so importing
 * the schema cannot start the application's router, store or i18n lifecycle.
 * An accidental network operation fails instead of silently succeeding.
 */
vi.mock('@/utils/request', () => {
  const unexpectedRequest = () => {
    throw new Error('Unexpected HTTP request in a lowcode renderer unit test')
  }
  return {
    TOKEN_KEY: 'lowcode-unit-test-token',
    get: unexpectedRequest,
    post: unexpectedRequest,
    put: unexpectedRequest,
    del: unexpectedRequest
  }
})

vi.mock('@/api/excel', () => ({
  triggerBlobDownload: () => {
    throw new Error('Unexpected download in a lowcode renderer unit test')
  }
}))
