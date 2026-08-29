export const splitSerialNumbers = (value: string) => [
  ...new Set(
    value
      .split(/[\s,，]+/)
      .map((item) => item.trim())
      .filter(Boolean)
  )
]

export const commerceIntentOf = (action: string, payload: unknown) =>
  `${action}:${JSON.stringify(payload)}`

export const createCommerceIntentStore = (keyFactory: () => string = () => crypto.randomUUID()) => {
  const keys = new Map<string, string>()
  return {
    key(intent: string) {
      const current = keys.get(intent)
      if (current) return current
      const created = keyFactory()
      keys.set(intent, created)
      return created
    },
    complete(intent: string) {
      keys.delete(intent)
    }
  }
}

export interface ProjectRouteContext {
  projectId: number
  projectVersion: number
  projectScopeVersion: number
}

const nonNegativeInteger = (value: unknown) => {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed >= 0 ? parsed : undefined
}

export const parseProjectRouteContext = (
  query: Record<string, unknown>
): ProjectRouteContext | undefined => {
  const projectId = nonNegativeInteger(query.projectId)
  const projectVersion = nonNegativeInteger(query.projectVersion)
  const projectScopeVersion = nonNegativeInteger(query.projectScopeVersion)
  if (!projectId || projectVersion === undefined || projectScopeVersion === undefined)
    return undefined
  return { projectId, projectVersion, projectScopeVersion }
}
