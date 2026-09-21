import { UserManager, WebStorageStateStore } from 'oidc-client-ts'

import { loadRuntimeConfig } from '@/config/runtime'

let userManagerPromise: Promise<UserManager> | undefined

export function getUserManager(): Promise<UserManager> {
  userManagerPromise ??= loadRuntimeConfig().then(
    (config) =>
      new UserManager({
        authority: config.oidcAuthority,
        client_id: config.oidcClientId,
        redirect_uri: `${window.location.origin}/auth/callback`,
        post_logout_redirect_uri: window.location.origin,
        response_type: 'code',
        scope: config.oidcScope,
        userStore: new WebStorageStateStore({ store: window.sessionStorage }),
        stateStore: new WebStorageStateStore({ store: window.sessionStorage }),
        automaticSilentRenew: true
      })
  )
  return userManagerPromise
}

export async function getAccessToken(): Promise<string | undefined> {
  const config = await loadRuntimeConfig()
  if (config.authMode === 'local') return undefined
  return (await (await getUserManager()).getUser())?.access_token
}
