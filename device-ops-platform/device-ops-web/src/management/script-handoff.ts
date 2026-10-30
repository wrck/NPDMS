export interface ScriptHandoff {
  content: string
  scriptKey: string
  scriptVersion: string
}

let pending: { token: symbol; script: ScriptHandoff } | undefined

/** Explicit user action only. Never persist script contents or put them in URLs. */
export function offerScript(script: ScriptHandoff): symbol {
  const token = Symbol('script-handoff')
  pending = { token, script: { ...script } }
  return token
}

export function revokeScript(token: symbol): void {
  if (pending?.token === token) pending = undefined
}

export function consumeScript(): ScriptHandoff | undefined {
  const script = pending?.script
  pending = undefined
  return script
}
