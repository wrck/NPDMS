// Only user presentation and business-widget discovery are outside this fixture.
// Authentication storage, HTTP transport, page APIs, router, renderer and pages are real.
export function useUserStore() { return { userInfo: { id: 7, userId: 7, name: 'Fixture user' } } }
export function componentMap() { return {} }
export async function initBuiltinComponents() {}
export default { get: () => undefined, list: () => [] }
