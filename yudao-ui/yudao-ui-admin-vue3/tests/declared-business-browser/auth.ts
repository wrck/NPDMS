// The backend fixture authenticates this random-password SQL user; no token is exposed to JS.
export const getCurrentUserId = () => sessionStorage.getItem('it-authenticated') ? 880001 : 0
export const getTenantId = () => 7
export const getVisitTenantId = () => undefined
