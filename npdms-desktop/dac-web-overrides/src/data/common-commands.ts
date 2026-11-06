/** Contract consumed by ScriptArtifactEditor; no authoritative catalogue was supplied. */
export interface CommonCommand {
  id: string
  label: string
  category: string
  command: string
}

// BLOCKED_BY_SPEC: the pinned baseline imports this module but contains no catalogue.
// Keep unavailable presets distinct from the user's manually entered script.
export const commonCommands: readonly CommonCommand[] = Object.freeze([])
export const commonCommandsUnavailableMessage = '常用命令目录缺失，暂不可用。请手动输入并核对命令。'
