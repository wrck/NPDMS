import { config } from '@/config/axios/config'

/** A phone opening the local dev server must address the API on that same host. */
export const customerConfirmationBaseUrl = () => {
  const base = new URL(config.base_url, window.location.origin)
  if (import.meta.env.DEV && ['localhost', '127.0.0.1', '[::1]'].includes(base.hostname)) {
    base.hostname = window.location.hostname
  }
  return base.toString().replace(/\/$/, '')
}
