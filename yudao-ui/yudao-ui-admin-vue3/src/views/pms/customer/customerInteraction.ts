export const customerIntentOf = (action: string, payload: unknown) =>
  `${action}:${JSON.stringify(payload)}`

export const createCustomerIntentStore = (keyFactory: () => string = () => crypto.randomUUID()) => {
  const keys = new Map<string, string>()

  return {
    key(intent: string) {
      const existing = keys.get(intent)
      if (existing) return existing
      const key = keyFactory()
      keys.set(intent, key)
      return key
    },
    complete(intent: string) {
      keys.delete(intent)
    }
  }
}
