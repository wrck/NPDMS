/** Browser fixture boundaries. The renderer, schema and nested consumers stay real. */
export const TOKEN_KEY = 'renderer-browser-fixture'
function unexpected(): never { throw new Error('Unexpected transport call in renderer browser fixture') }
export const get = unexpected
export const post = unexpected
export const put = unexpected
export const del = unexpected
export const triggerBlobDownload = unexpected
export const componentMap = () => ({})
export const initBuiltinComponents = async () => undefined
export const useUserStore = () => ({ userInfo: { userId: 'fixture-user' } })
