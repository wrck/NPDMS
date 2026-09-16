import type { ComponentMeta, RegisteredComponent } from './types'

const registry = new Map<string, RegisteredComponent>()

export function register(name: string, component: any, meta: ComponentMeta) {
  registry.set(name, { component, meta })
}

export function get(name: string) {
  return registry.get(name)
}

export function list() {
  return Array.from(registry.values()).map((v) => v.meta)
}

export function has(name: string) {
  return registry.has(name)
}

/**
 * Registry default export (aggregated API).
 * Usage: `import LowCodeComponentRegistry from '@/components/LowCodeComponentRegistry'`
 */
export default {
  register,
  get,
  list,
  has,
  initBuiltinComponents
}

// Hardcoded metas for 15 builtin widgets (used as reliable fallback
// when backend API is unavailable, e.g. offline or network error).
const BUILTIN_METAS: Record<string, ComponentMeta> = {
  UserSelector: { name: 'UserSelector', displayName: '用户选择器', category: 'SELECTOR', propsSchema: [{ key: 'multiple', type: 'boolean', default: false }] },
  DeptSelector: { name: 'DeptSelector', displayName: '部门选择器', category: 'SELECTOR', propsSchema: [{ key: 'multiple', type: 'boolean', default: false }] },
  DictSelect: { name: 'DictSelect', displayName: '数据字典下拉', category: 'SELECTOR', propsSchema: [{ key: 'dictCode', type: 'string', required: true }] },
  FileUploader: { name: 'FileUploader', displayName: '文件上传', category: 'INPUT', propsSchema: [{ key: 'accept', type: 'string' }, { key: 'maxSize', type: 'number', default: 10 }] },
  RichTextEditor: { name: 'RichTextEditor', displayName: '富文本编辑器', category: 'INPUT', propsSchema: [{ key: 'height', type: 'number', default: 300 }] },
  CodeEditor: { name: 'CodeEditor', displayName: '代码编辑器', category: 'INPUT', propsSchema: [{ key: 'language', type: 'string', default: 'javascript' }] },
  ColorPicker: { name: 'ColorPicker', displayName: '颜色选择器', category: 'INPUT', propsSchema: [{ key: 'showAlpha', type: 'boolean', default: true }] },
  TreeSelect: { name: 'TreeSelect', displayName: '树形选择', category: 'SELECTOR', propsSchema: [{ key: 'data', type: 'array' }] },
  DateRangePicker: { name: 'DateRangePicker', displayName: '日期范围', category: 'INPUT', propsSchema: [{ key: 'format', type: 'string', default: 'YYYY-MM-DD' }] },
  NumberRangeInput: { name: 'NumberRangeInput', displayName: '数字范围', category: 'INPUT', propsSchema: [{ key: 'min', type: 'number' }, { key: 'max', type: 'number' }] },
  AddressPicker: { name: 'AddressPicker', displayName: '地址选择', category: 'SELECTOR', propsSchema: [{ key: 'level', type: 'number', default: 3 }] },
  BarcodeInput: { name: 'BarcodeInput', displayName: '条码扫描', category: 'INPUT', propsSchema: [{ key: 'types', type: 'array', default: ['CODE_128', 'EAN_13'] }] },
  SignaturePad: { name: 'SignaturePad', displayName: '电子签名', category: 'INPUT', propsSchema: [{ key: 'width', type: 'number', default: 400 }, { key: 'height', type: 'number', default: 200 }] },
  ChartPreview: { name: 'ChartPreview', displayName: '图表预览', category: 'DISPLAY', propsSchema: [{ key: 'chartType', type: 'string', default: 'bar' }] },
  QrcodeDisplay: { name: 'QrcodeDisplay', displayName: '二维码展示', category: 'DISPLAY', propsSchema: [{ key: 'size', type: 'number', default: 128 }] }
}

/**
 * Parse backend propsSchema (JSON string) into ComponentPropDef[].
 *
 * Backend format: '{"props":[{"key":"multiple","type":"boolean","default":false}]}'
 * or directly '[{"key":"multiple",...}]'
 * Frontend expects: ComponentPropDef[] (array)
 */
function parsePropsSchema(raw: string | undefined): any[] {
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    // Handle {"props": [...]} wrapper format
    if (Array.isArray(parsed)) return parsed
    if (parsed && Array.isArray(parsed.props)) return parsed.props
    return []
  } catch {
    console.warn('[LowCode] Failed to parse propsSchema:', raw)
    return []
  }
}

/**
 * Initialize builtin components:
 * 1. Load local .vue widgets with hardcoded metas (reliable baseline)
 * 2. Fetch backend component metas and merge (adds custom/marketplace components)
 *
 * Errors are logged but do not block the designer — base components remain usable.
 * Widgets are loaded concurrently to avoid blocking the designer UI.
 */
export async function initBuiltinComponents() {
  // Step 1: Load local widget .vue files concurrently with hardcoded metas
  const widgets = import.meta.glob('../LowCodeWidgets/*.vue')
  const loadPromises: Promise<void>[] = []
  for (const [path, loader] of Object.entries(widgets)) {
    const name = path.split('/').pop()!.replace('.vue', '')
    const meta = BUILTIN_METAS[name]
    if (!meta) continue
    loadPromises.push(
      (loader as () => Promise<any>)()
        .then((module) => {
          const component = module.default
          register(name, component, meta)
        })
        .catch((e) => {
          console.error(`[LowCode] Failed to load widget "${name}":`, e)
        })
    )
  }
  await Promise.all(loadPromises)
  console.info(`[LowCode] Loaded ${registry.size} builtin widgets`)

  // Step 2: Fetch backend component metas (best-effort, with timeout protection)
  try {
    const { listComponentMetas } = await import('@/api/lowcode-component-meta')
    // Race against a 3s timeout to avoid blocking the designer
    const timeoutPromise = new Promise<never>((_, reject) =>
      setTimeout(() => reject(new Error('timeout')), 3000)
    )
    const remoteMetas = await Promise.race([listComponentMetas(), timeoutPromise])
    let remoteCount = 0
    for (const rm of remoteMetas) {
      // Skip if already registered as builtin (local .vue takes priority)
      if (registry.has(rm.name)) continue

      // Only register remote components that have an entryUrl (can be dynamically loaded)
      if (rm.sourceType === 'MARKETPLACE' && rm.entryUrl) {
        try {
          const module = await import(/* @vite-ignore */ rm.entryUrl)
          const component = module.default || module
          const meta: ComponentMeta = {
            name: rm.name,
            displayName: rm.displayName,
            category: rm.category,
            propsSchema: parsePropsSchema(rm.propsSchema)
          }
          register(rm.name, component, meta)
          remoteCount++
        } catch (e) {
          console.error(`[LowCode] Failed to load remote component "${rm.name}" from ${rm.entryUrl}:`, e)
        }
      }
    }
    if (remoteCount > 0) {
      console.info(`[LowCode] Loaded ${remoteCount} remote components from backend`)
    }
  } catch (e) {
    // Backend API unavailable or timeout — silent fallback, builtin widgets already loaded
    console.warn('[LowCode] Could not fetch backend component metas (builtin widgets still available):', e)
  }
}
