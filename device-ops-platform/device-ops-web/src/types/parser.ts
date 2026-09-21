export type SemanticParserMode = 'AUTO' | 'RELEASE' | 'DISABLED'

export interface SemanticParserSelection {
  mode: SemanticParserMode
  releaseId?: string
  logType?: string
}

export interface SemanticParsingRequest {
  enabled: boolean
  logType?: string
  releaseId?: string
  inputFormat?: 'command-output-block/v1'
}

export interface ParserCoordinate {
  logType: string
  releaseVersion: string
  engineVersion: string
  ruleVersion: string
  projectionVersion: string
  extensionId?: string
  extensionVersion?: string
}

export interface ParserOption {
  releaseId: string
  logType: string
  displayName: string
  releaseVersion: string
  coordinate: ParserCoordinate
  activeDefault: boolean
}

export interface ParserSelectionOptions {
  automaticEnabled: boolean
  defaultAvailable: boolean
  defaultReleaseId?: string
  options: ParserOption[]
}

export type ParseTaskState =
  | 'QUEUED'
  | 'RUNNING'
  | 'WAITING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CANCELLED'

export type ObservationStatus =
  | 'OBSERVED'
  | 'NO_DATA'
  | 'NOT_ENABLED'
  | 'REDACTED'
  | 'PARTIAL'
  | 'UNPARSED'
  | 'SOURCE_CORRUPTED'
  | 'EXECUTION_FAILED'

export interface BlockObservation {
  commandIndex: number
  blockRole?: string
  status: ObservationStatus
  confidence: number
  sourceLineStart?: number
  sourceLineEnd?: number
  matchedRuleIds: string[]
  warnings: string[]
}

export interface NestedBlockObservation {
  parentCommandIndex: number
  sectionIndex: number
  nestingDepth: number
  commandText: string
  blockRole?: string
  status: ObservationStatus
  confidence: number
  sourceLineStart: number
  sourceLineEnd: number
  matchedRuleIds: string[]
  warnings: string[]
}

export interface ModelProfileSelection {
  profileId: string
  normalizedModel?: string
  source: 'LOG_OUTPUT' | 'CONTEXT_SNAPSHOT' | 'GENERIC'
  commandIndex?: number
  lineNumber?: number
  warnings: string[]
}

export type GenericStructureStatus = 'STRUCTURED' | 'PARTIAL' | 'TEXT_ONLY' | 'EMPTY' | 'LIMITED'

export interface GenericWarning { code: string; lineNumber: number }
export interface GenericKeyValueEntry {
  key: string; value: string; startLine: number; endLine: number; children: GenericKeyValueEntry[]
}
export interface GenericSectionBase {
  sectionIndex: number; type: string; startLine: number; endLine: number
  rawLines: string[]; warnings: GenericWarning[]
}
export interface GenericKeyValueSection extends GenericSectionBase {
  type: 'keyValue' | 'keyValueTree'; entries: GenericKeyValueEntry[]; data: Record<string, unknown>
}
export interface GenericTableColumn { id: string; label: string | null; index: number }
export interface GenericTableRow { rowIndex: number; values: Record<string, string | null> }
export interface GenericUnparsedLine { lineNumber: number; value: string }
export interface GenericTableSection extends GenericSectionBase {
  type: 'table'; columns: GenericTableColumn[]; rows: GenericTableRow[]; unparsedLines: GenericUnparsedLine[]
}
export interface GenericRecordValue {
  recordIndex: number; identity: string | null; startLine: number; endLine: number
  entries: GenericKeyValueEntry[]; sections: GenericSection[]
}
export interface GenericRecordListSection extends GenericSectionBase { type: 'recordList'; records: GenericRecordValue[] }
export interface GenericConfigStanza { header: string; startLine: number; endLine: number; lines: string[] }
export interface GenericConfigSection extends GenericSectionBase { type: 'configStanza'; stanzas: GenericConfigStanza[] }
export interface GenericListItem { itemIndex: number; value: string; startLine: number; endLine: number }
export interface GenericListSection extends GenericSectionBase { type: 'list'; items: GenericListItem[] }
export interface GenericTextLine { lineNumber: number; value: string }
export interface GenericTextSection extends GenericSectionBase { type: 'text'; lines: GenericTextLine[] }
export interface GenericUnknownSection extends GenericSectionBase { type: string; [key: string]: unknown }
export type GenericSection = GenericKeyValueSection | GenericTableSection | GenericRecordListSection
  | GenericConfigSection | GenericListSection | GenericTextSection | GenericUnknownSection
export interface GenericUnit {
  commandIndex: number; parentCommandIndex?: number | null; sectionIndex?: number | null
  nestingDepth: number; commandText: string; sourceLineStart: number; sourceLineEnd: number
  structureStatus: GenericStructureStatus; sections: GenericSection[]; warnings: GenericWarning[]
  omittedLineCount?: number
}
export interface GenericContent { units: GenericUnit[] }

export interface SemanticParseResult {
  schemaVersion: string
  parserVersion: string
  ruleVersion: string
  projectionVersion: string
  snapshot: Record<string, unknown>
  genericContent?: GenericContent
  projections: Record<string, unknown>
  profileSelection?: ModelProfileSelection
  quality: Record<string, unknown>
  observations: BlockObservation[]
  nestedObservations?: NestedBlockObservation[]
}

export interface ParseResultEnvelope {
  resultId: string
  taskId: string
  releaseId: string
  coordinate: ParserCoordinate
  contextSnapshot: Record<string, unknown>
  semanticResult: SemanticParseResult
  createdAt: string
}

export interface CollectionSemanticResult {
  targetId: number
  taskId: string
  state: ParseTaskState
  waitReason?: string
  releaseId: string
  coordinate: ParserCoordinate
  resultId?: string
  result?: ParseResultEnvelope
}
