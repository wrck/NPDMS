export interface SemanticEvidenceRow {
  semanticKey: string
  value: unknown
  status?: string
  confidence?: number
  ruleId?: string
  commandIndex?: number
  commandText?: string
  lineStart?: number
  lineEnd?: number
  nestingDepth?: number
  nestedCommandText?: string
  sectionIndex?: number
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

export function flattenSemanticEvidence(value: unknown): SemanticEvidenceRow[] {
  const rows: SemanticEvidenceRow[] = []
  function visit(node: unknown) {
    if (Array.isArray(node)) {
      node.forEach(visit)
      return
    }
    if (!isRecord(node)) return
    if (typeof node.semanticKey === 'string' && 'value' in node) {
      const source = isRecord(node.source) ? node.source : {}
      rows.push({
        semanticKey: node.semanticKey,
        value: node.value,
        status: typeof node.status === 'string' ? node.status : undefined,
        confidence: typeof node.confidence === 'number' ? node.confidence : undefined,
        ruleId: typeof node.ruleId === 'string' ? node.ruleId : undefined,
        commandIndex: typeof source.commandIndex === 'number' ? source.commandIndex : undefined,
        commandText: typeof source.commandText === 'string' ? source.commandText : undefined,
        lineStart: typeof source.lineStart === 'number' ? source.lineStart : undefined,
        lineEnd: typeof source.lineEnd === 'number' ? source.lineEnd : undefined,
        nestingDepth: typeof source.nestingDepth === 'number' ? source.nestingDepth : undefined,
        nestedCommandText: typeof source.nestedCommandText === 'string'
          ? source.nestedCommandText
          : undefined,
        sectionIndex: typeof source.sectionIndex === 'number' ? source.sectionIndex : undefined
      })
      return
    }
    Object.values(node).forEach(visit)
  }
  visit(value)
  return rows
}

export function displaySemanticValue(value: unknown): string {
  if (value == null) return '—'
  return typeof value === 'object' ? JSON.stringify(value) : String(value)
}
