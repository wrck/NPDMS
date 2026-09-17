/** Isolated Web Storage boundary for state-only Node tests; not a DOM emulator. */
export function createMemoryStorage(): Storage {
  const values = new Map<string, string>()
  return {
    get length() { return values.size },
    clear() { values.clear() },
    getItem(key: string) { return values.get(String(key)) ?? null },
    key(index: number) { return Array.from(values.keys())[index] ?? null },
    removeItem(key: string) { values.delete(String(key)) },
    setItem(key: string, value: string) { values.set(String(key), String(value)) }
  }
}
