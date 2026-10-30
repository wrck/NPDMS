import {
  existsSync,
  mkdirSync,
  readFileSync,
  renameSync,
  rmSync,
  unlinkSync,
  writeFileSync
} from 'node:fs'
import { join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

import { validateProjectionProfiles, validateRules, validateStructuredLog } from './contracts.mjs'
import { applyProjectionProfiles } from './projection-engine.mjs'
import { extractFacts, inferBlockMeaning, redactSensitiveText } from './rule-engine.mjs'
import { assembleSnapshot } from './snapshot-assembler.mjs'

const TOOL_ROOT = fileURLToPath(new URL('..', import.meta.url))
const DEFAULT_RULES = join(TOOL_ROOT, 'rules', 'show-tech-semantic-rules.json')
const DEFAULT_PROJECTIONS = join(TOOL_ROOT, 'projections', 'projection-profiles.json')
const DEFAULT_SCHEMA = join(TOOL_ROOT, 'schema', 'canonical-device-schema.json')
const ARTIFACT_NAMES = Object.freeze([
  'canonical-device-schema.json',
  'show-tech.semantic-map.json',
  'show-tech.device-snapshot.json',
  'projection-profiles.json',
  'mapping-quality-report.json',
  'README.md'
])

const ROLE_MEANINGS = Object.freeze({
  DEVICE_CLOCK: '设备当前时间与时区', DEVICE_VERSION: '设备身份、软件和硬件摘要', SOFTWARE_PATCH: '软件补丁清单',
  HARDWARE_SLOT: '槽位与板卡状态', LOCAL_USER: '本地账号与服务权限', MANAGEMENT_TIMEOUT: '管理通道超时策略',
  SNMP_SERVICE: 'SNMP 服务、位置与联系人', CONSOLE_SERVICE: 'Console 串口参数', SSH_SERVICE: 'SSH 服务能力',
  TELNET_SERVICE: 'Telnet 服务能力', LOCAL_GROUP: '本地权限组', CONFIG_ROLLBACK: '配置回滚能力',
  CONFIG_CONSISTENCY: '运行配置与保存配置一致性', CONFIG_SCHEDULED_SAVE: '定时保存策略', ENVIRONMENT_HEALTH: '温度与风扇等环境健康',
  MEMORY_USAGE: '设备内存使用情况', CPU_USAGE: '分核 CPU 使用率', PROCESS_USAGE: '进程资源占用',
  HOT_BACKUP_CONFIG: '热备配置', HOT_BACKUP_STATE: '热备运行状态', VRRP_SUMMARY: 'VRRP 实例摘要',
  VRRP_STATISTICS: 'VRRP 统计', INTERFACE_DETAIL: '接口详细状态与计数器', INTERFACE_IP_SUMMARY: '接口、协议状态与 IP 摘要',
  INTERFACE_STATUS: '物理端口状态', INTERFACE_COUNTERS: '接口流量计数', SESSION_SUMMARY: '会话总量与协议分布',
  SESSION_AGING: '会话老化策略', SESSION_TOP_SOURCE: '源地址会话 TopN', SESSION_TOP_DESTINATION: '目的地址会话 TopN',
  NTP_ASSOCIATIONS: 'NTP 对端', NTP_STATUS: 'NTP 启用和同步状态', ARP_SUMMARY: 'ARP 数量摘要',
  ARP_TABLE: 'ARP 邻接表', MAC_TABLE: '二层 MAC 转发表', ROUTE_TABLE: 'IP 路由表', ROUTE_SUMMARY: '路由数量摘要',
  OSPF_NEIGHBORS: 'OSPF 状态与邻居', ISIS_NEIGHBORS: 'ISIS 邻居', BGP_SUMMARY: 'BGP 摘要与邻居',
  BGP_VPNV4_SUMMARY: 'VPNv4 BGP 摘要', POLICY_MAP: '策略映射', APPLICATION_GATEWAYS: '应用层网关开关',
  TRACKED_OBJECTS: 'IP 跟踪对象', LOG_CAPACITY: '日志缓冲区容量', OPERATION_EVENTS: '操作审计事件',
  SYSTEM_EVENTS: '系统事件', DIAGNOSTIC_EVENTS: '诊断事件', TECH_SUPPORT_CONFIG: '技术支持采集配置',
  RUNNING_CONFIG: '当前运行配置及通用配置段'
})

function readJson(path) {
  return JSON.parse(readFileSync(path, 'utf8').replace(/^\uFEFF/, ''))
}

function writeJson(path, value) {
  writeFileSync(path, `${JSON.stringify(value, null, 2)}\n`, 'utf8')
}

function parseArgs(argv) {
  const allowed = new Set(['--input', '--output', '--rules', '--projections'])
  const options = {}
  for (let index = 0; index < argv.length; index += 2) {
    const flag = argv[index]
    const value = argv[index + 1]
    if (!allowed.has(flag)) throw new TypeError(`unsupported option: ${flag ?? '<missing>'}`)
    if (!value || value.startsWith('--')) throw new TypeError(`${flag} requires a value`)
    if (flag.slice(2) in options) throw new TypeError(`duplicate option: ${flag}`)
    options[flag.slice(2)] = value
  }
  if (!options.input || !options.output) throw new TypeError('--input and --output are required')
  return options
}

function countStatuses(observations) {
  const counts = {}
  for (const observation of observations) counts[observation.status] = (counts[observation.status] ?? 0) + 1
  return counts
}

function corruptedLineCount(input) {
  if (Array.isArray(input.quality?.replacementCharacterLines)) return input.quality.replacementCharacterLines.length
  return input.stdout.blocks.reduce((count, block) => count + block.lines.filter((line) => line.includes('\uFFFD')).length, 0)
}

function countPlaintextSecretAssignments(value) {
  if (typeof value === 'string') return redactSensitiveText(value) === value ? 0 : 1
  if (Array.isArray(value)) return value.reduce((count, item) => count + countPlaintextSecretAssignments(item), 0)
  if (value && typeof value === 'object') {
    return Object.values(value).reduce((count, item) => count + countPlaintextSecretAssignments(item), 0)
  }
  return 0
}

function semanticDictionary(rules) {
  const dictionary = {}
  for (const rule of rules) {
    const key = rule.target.semanticKey
    const current = dictionary[key] ?? {
      description: ROLE_MEANINGS[rule.blockRole] ?? rule.blockRole,
      dataType: rule.target.dataType,
      cardinality: rule.target.cardinality,
      blockRoles: [],
      aliases: []
    }
    if (!current.blockRoles.includes(rule.blockRole)) current.blockRoles.push(rule.blockRole)
    for (const alias of rule.aliases ?? []) if (!current.aliases.includes(alias)) current.aliases.push(alias)
    dictionary[key] = current
  }
  return Object.fromEntries(Object.entries(dictionary).sort(([left], [right]) => left.localeCompare(right)))
}

function outputReadme(sourceName) {
  return `# 设备日志语义映射结果

源文件：${sourceName}

- \`show-tech.semantic-map.json\`：每个内容块的业务角色、状态、目标字段和证据位置。
- \`show-tech.device-snapshot.json\`：标准分层实体；事实字段保留 \`value/source/ruleId/confidence\`。
- \`projection-profiles.json\`：业务扁平映射及本次结果，例如 \`results.deviceBasic.softVersion\`。
- \`mapping-quality-report.json\`：覆盖率、空块、未启用、源损坏、冲突和告警。
- \`canonical-device-schema.json\`：结构契约及 \`x-semanticKeys\` 字段字典。

快速查询软件版本：读取 \`projection-profiles.json -> results.deviceBasic.softVersion\`。
需要回溯时：按快照事实的 \`source.blockIndex\` 和 \`source.lineStart\` 定位源内容块。
`
}

function safelyPublish(tempDirectory, outputDirectory) {
  mkdirSync(outputDirectory, { recursive: true })
  for (const name of ARTIFACT_NAMES) {
    const target = join(outputDirectory, name)
    if (existsSync(target)) unlinkSync(target)
    renameSync(join(tempDirectory, name), target)
  }
  rmSync(tempDirectory, { recursive: true, force: true })
}

export function generateSemanticArtifacts(options) {
  const inputPath = resolve(options.input)
  const outputDirectory = resolve(options.output)
  const rulesPath = resolve(options.rules ?? DEFAULT_RULES)
  const projectionsPath = resolve(options.projections ?? DEFAULT_PROJECTIONS)
  const input = validateStructuredLog(readJson(inputPath))
  const ruleCatalog = validateRules(readJson(rulesPath))
  const projectionCatalog = validateProjectionProfiles(readJson(projectionsPath))
  const schema = readJson(DEFAULT_SCHEMA)
  schema['x-semanticKeys'] = semanticDictionary(ruleCatalog.rules)

  const observations = []
  const facts = []
  const warnings = []
  for (const block of input.stdout.blocks) {
    const observation = inferBlockMeaning(block, ruleCatalog.rules)
    observations.push({ ...observation, businessMeaning: ROLE_MEANINGS[observation.blockRole] ?? null })
    const matched = ruleCatalog.rules.filter((rule) => observation.matchedRuleIds.includes(rule.ruleId))
    const extracted = extractFacts(block, matched)
    facts.push(...extracted.facts)
    warnings.push(...extracted.warnings)
  }

  const snapshot = assembleSnapshot({
    generatedAt: new Date().toISOString(),
    collectionId: input.session?.collectionId ?? null,
    namespace: input.session?.namespace ?? null,
    source: input.source ?? { fileName: inputPath.split(/[\\/]/).at(-1) }
  }, observations, facts, warnings)
  const results = applyProjectionProfiles(snapshot, projectionCatalog.profiles)
  const semanticMap = {
    schemaVersion: '1.0.0',
    collectionId: snapshot.collection.collectionId,
    source: snapshot.collection.source,
    observations
  }
  const statuses = countStatuses(observations)
  const quality = {
    schemaVersion: '1.0.0',
    blockCount: observations.length,
    mappedBlockCount: observations.length - (statuses.UNPARSED ?? 0),
    blockCoverage: observations.length ? (observations.length - (statuses.UNPARSED ?? 0)) / observations.length : 1,
    mappedFactCount: snapshot.quality.mappedFactCount,
    statusCounts: statuses,
    emptyBlockCount: statuses.NO_DATA ?? 0,
    disabledBlockCount: statuses.NOT_ENABLED ?? 0,
    unparsedBlockCount: statuses.UNPARSED ?? 0,
    sourceCorruptedBlockCount: statuses.SOURCE_CORRUPTED ?? 0,
    sourceCorruptedLineCount: corruptedLineCount(input),
    conflictCount: snapshot.quality.conflicts.length,
    conflicts: snapshot.quality.conflicts,
    warningCount: warnings.length,
    warnings,
    unmapped: snapshot.unmapped,
    plaintextSecretFindingCount: countPlaintextSecretAssignments({ facts, results })
  }
  const generatedProfiles = { ...projectionCatalog, results }

  const tempDirectory = `${outputDirectory}.tmp-${process.pid}`
  if (existsSync(tempDirectory)) rmSync(tempDirectory, { recursive: true, force: true })
  mkdirSync(tempDirectory, { recursive: true })
  try {
    writeJson(join(tempDirectory, 'canonical-device-schema.json'), schema)
    writeJson(join(tempDirectory, 'show-tech.semantic-map.json'), semanticMap)
    writeJson(join(tempDirectory, 'show-tech.device-snapshot.json'), snapshot)
    writeJson(join(tempDirectory, 'projection-profiles.json'), generatedProfiles)
    writeJson(join(tempDirectory, 'mapping-quality-report.json'), quality)
    writeFileSync(join(tempDirectory, 'README.md'), outputReadme(input.source?.fileName ?? inputPath), 'utf8')
    safelyPublish(tempDirectory, outputDirectory)
  } catch (error) {
    if (existsSync(tempDirectory)) rmSync(tempDirectory, { recursive: true, force: true })
    throw error
  }
  return { outputDirectory, semanticMap, snapshot, projections: generatedProfiles, quality, schema }
}

function main() {
  try {
    const options = parseArgs(process.argv.slice(2))
    const result = generateSemanticArtifacts(options)
    process.stdout.write(`Generated ${ARTIFACT_NAMES.length} semantic artifacts in ${result.outputDirectory}\n`)
  } catch (error) {
    process.stderr.write(`Semantic mapping failed: ${error.message}\n`)
    process.exitCode = 1
  }
}

if (process.argv[1] && fileURLToPath(import.meta.url) === resolve(process.argv[1])) main()
