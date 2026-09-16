<script setup lang="ts">
/**
 * 表达式编辑器组件（借鉴 Budibase Bindings Drawer）。
 *
 * <p>基于 &lt;textarea&gt; + 叠加 &lt;pre&gt; 实现简易语法高亮，不引入 monaco/codemirror
 * 等重量级编辑器，避免打包体积过大。布局：</p>
 * <ul>
 *   <li>左侧：表达式文本框（透明文字 + 下方 pre 着色，光标可编辑）</li>
 *   <li>右侧：变量/字段侧栏（点击插入到光标位置）</li>
 *   <li>底部：函数库提示（math / string / date，点击插入）</li>
 *   <li>最底部：语法校验状态栏（实时检查括号 / 引号 / 运算符，debounce 300ms）</li>
 * </ul>
 *
 * <p>插入变量格式：Aviator 用 <code>${变量名}</code>，Groovy/JavaScript 用 <code>变量名</code>。
 * 通过 v-model 双向绑定 modelValue。</p>
 */
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'

defineOptions({ name: 'ExpressionEditor' })

/** 表达式语言类型 */
type ExpressionLanguage = 'groovy' | 'aviator' | 'javascript'

/** 字段项结构 */
interface FieldItem {
  name: string
  type?: string
}

/** 变量项：既支持纯字符串名（task 规格），也兼容旧调用方传入的 {name, type} 对象 */
type VariableItem = string | FieldItem

/** 校验结果 */
interface ValidationResult {
  valid: boolean
  error?: string
}

const props = withDefaults(
  defineProps<{
    /** 表达式字符串（v-model） */
    modelValue: string
    /** 语言：groovy / aviator / javascript，默认 aviator */
    language?: ExpressionLanguage
    /** 可用变量名列表（也可传 {name, type} 对象数组以显示类型） */
    variables?: VariableItem[]
    /** 可用字段列表 */
    fields?: FieldItem[]
    /** 编辑区高度（px），默认 200 */
    height?: number
  }>(),
  {
    language: 'aviator',
    variables: () => [],
    fields: () => [],
    height: 200
  }
)

const emit = defineEmits<{ (e: 'update:modelValue', v: string): void }>()

// ===================== v-model 双向绑定 =====================

const text = ref(props.modelValue || '')
watch(
  () => props.modelValue,
  (v) => {
    if (v !== text.value) text.value = v ?? ''
  }
)

// ===================== textarea + 光标跟踪 =====================

const textareaRef = ref<HTMLTextAreaElement | null>(null)
const preRef = ref<HTMLPreElement | null>(null)

function onInput(e: Event) {
  const ta = e.target as HTMLTextAreaElement
  text.value = ta.value
  emit('update:modelValue', ta.value)
}

/** 同步 pre 高亮层滚动位置，保证与 textarea 对齐 */
function onScroll(e: Event) {
  const ta = e.target as HTMLTextAreaElement
  if (preRef.value) {
    preRef.value.scrollTop = ta.scrollTop
    preRef.value.scrollLeft = ta.scrollLeft
  }
}

// ===================== 插入到光标位置 =====================

/** 变量插入文本：aviator 用 ${name}，其余用裸名 */
function insertTextFor(name: string): string {
  return props.language === 'aviator' ? `\${${name}}` : name
}

/**
 * 在 textarea 光标处插入文本。textarea 失焦后 selectionStart/End 仍保留上次位置，
 * 故点击侧栏 chip 时可直接读取，无需额外缓存。插入后焦点回到 textarea 并定位光标。
 */
function insertAtCursor(insertText: string) {
  const ta = textareaRef.value
  if (!ta) {
    // 无 textarea 时回退为追加
    const next = text.value + insertText
    text.value = next
    emit('update:modelValue', next)
    return
  }
  const start = ta.selectionStart ?? 0
  const end = ta.selectionEnd ?? 0
  const before = text.value.slice(0, start)
  const after = text.value.slice(end)
  const next = before + insertText + after
  text.value = next
  emit('update:modelValue', next)
  nextTick(() => {
    ta.focus()
    const pos = start + insertText.length
    ta.setSelectionRange(pos, pos)
  })
}

/** 变量名提取：字符串取本身，对象取 name */
function varName(v: VariableItem): string {
  return typeof v === 'string' ? v : v.name
}

/** 变量标签：字符串显示本身，对象显示 name (type) */
function varLabel(v: VariableItem): string {
  if (typeof v === 'string') return v
  return v.type ? `${v.name} (${v.type})` : v.name
}

function onVarClick(v: VariableItem) {
  insertAtCursor(insertTextFor(varName(v)))
}

function onFieldClick(f: FieldItem) {
  insertAtCursor(insertTextFor(f.name))
}

// ===================== 函数库 =====================

interface FuncItem {
  label: string
  insertText: string
  description: string
}
interface FuncGroup {
  key: string
  label: string
  items: FuncItem[]
}

/** 内置函数库（math / string / date 常用函数，点击插入到光标） */
const FUNCTION_LIBRARY: FuncGroup[] = [
  {
    key: 'math',
    label: 'math',
    items: [
      { label: 'abs(x)', insertText: 'abs(x)', description: '绝对值' },
      { label: 'ceil(x)', insertText: 'ceil(x)', description: '向上取整' },
      { label: 'floor(x)', insertText: 'floor(x)', description: '向下取整' }
    ]
  },
  {
    key: 'string',
    label: 'string',
    items: [
      { label: 'length(s)', insertText: 'length(s)', description: '字符串长度' },
      {
        label: 'substring(s, start, end)',
        insertText: 'substring(s, start, end)',
        description: '截取子串'
      }
    ]
  },
  {
    key: 'date',
    label: 'date',
    items: [
      { label: 'now()', insertText: 'now()', description: '当前时间戳' },
      {
        label: 'format(date, pattern)',
        insertText: 'format(date, "yyyy-MM-dd")',
        description: '按指定格式格式化日期'
      }
    ]
  }
]

function onFuncClick(item: FuncItem) {
  insertAtCursor(item.insertText)
}

// ===================== 简易语法高亮（tokenize → span 列表） =====================

interface Token {
  text: string
  cls: string
}

/** 判断是否标识符起始字符（避免在循环里反复构造正则） */
function isIdentStart(ch: string): boolean {
  return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || ch === '_'
}
function isIdentPart(ch: string): boolean {
  return isIdentStart(ch) || (ch >= '0' && ch <= '9')
}
function isDigit(ch: string): boolean {
  return ch >= '0' && ch <= '9'
}
function isSpace(ch: string): boolean {
  return ch === ' ' || ch === '\t' || ch === '\n' || ch === '\r'
}

/**
 * 简易词法分析：单趟扫描生成 token 列表，避免正则叠加导致的嵌套匹配问题。
 * 识别：aviator ${var}、字符串、数字、函数调用（标识符后跟 ( ）、普通标识符、其他字符。
 */
function tokenize(code: string, lang: ExpressionLanguage): Token[] {
  const tokens: Token[] = []
  const n = code.length
  let i = 0
  while (i < n) {
    const ch = code[i]

    // aviator 变量 ${...}
    if (lang === 'aviator' && ch === '$' && code[i + 1] === '{') {
      const end = code.indexOf('}', i + 2)
      const stop = end === -1 ? n : end + 1
      tokens.push({ text: code.slice(i, stop), cls: 'ee-tok-var' })
      i = stop
      continue
    }

    // 字符串字面量 "..." 或 '...'
    if (ch === '"' || ch === "'") {
      let j = i + 1
      while (j < n && code[j] !== ch) {
        if (code[j] === '\\') j++
        j++
      }
      j = Math.min(j + 1, n)
      tokens.push({ text: code.slice(i, j), cls: 'ee-tok-str' })
      i = j
      continue
    }

    // 数字
    if (isDigit(ch)) {
      let j = i
      while (j < n && (isDigit(code[j]) || code[j] === '.')) j++
      tokens.push({ text: code.slice(i, j), cls: 'ee-tok-num' })
      i = j
      continue
    }

    // 标识符 / 函数调用
    if (isIdentStart(ch)) {
      let j = i
      while (j < n && isIdentPart(code[j])) j++
      const word = code.slice(i, j)
      // 向后跳过空白，判断是否紧跟 ( 以识别函数调用
      let k = j
      while (k < n && isSpace(code[k])) k++
      const isFn = code[k] === '('
      tokens.push({ text: word, cls: isFn ? 'ee-tok-fn' : 'ee-tok-id' })
      i = j
      continue
    }

    // 其他单字符
    tokens.push({ text: ch, cls: 'ee-tok-plain' })
    i++
  }
  return tokens
}

const tokens = computed(() => tokenize(text.value, props.language))

// ===================== 语法校验（简化版） =====================

/** Aviator 合法运算符集合（含多字符运算符） */
const AVIATOR_OPERATORS = new Set([
  '+', '-', '*', '/', '%',
  '>', '<', '==', '!=', '>=', '<=',
  '&&', '||', '!', '?', ':'
])

/**
 * 移除字符串字面量后的表达式（用于结构校验，避免字符串内字符被误判）。
 *
 * <p>对 `"..."` / `'...'` 进行扫描，跳过转义字符；未闭合的字符串保留起始引号
 * 占位以便后续校验识别为「未闭合」。</p>
 */
function stripStrings(expr: string): { stripped: string; unclosedQuote: string | null } {
  let out = ''
  let i = 0
  const n = expr.length
  while (i < n) {
    const ch = expr[i]
    if (ch === '"' || ch === "'") {
      // 寻找匹配的结束引号
      let j = i + 1
      while (j < n && expr[j] !== ch) {
        if (expr[j] === '\\') j++
        j++
      }
      if (j >= n) {
        // 未闭合：保留起始引号，便于后续判断
        out += ch
        return { stripped: out, unclosedQuote: ch }
      }
      // 闭合：用空格替换字符串内容（保留长度，避免相邻 token 误连）
      out += ' '
      i = j + 1
      continue
    }
    out += ch
    i++
  }
  return { stripped: out, unclosedQuote: null }
}

/** 校验括号匹配：返回首个不匹配的括号信息（null 表示匹配） */
function checkBrackets(s: string): { char: string; pos: number; kind: 'mismatch' | 'unclosed' } | null {
  const stack: Array<{ ch: string; pos: number }> = []
  const pairs: Record<string, string> = { ')': '(', ']': '[', '}': '{' }
  const opens = new Set(['(', '[', '{'])
  for (let i = 0; i < s.length; i++) {
    const ch = s[i]
    if (opens.has(ch)) {
      stack.push({ ch, pos: i })
    } else if (ch in pairs) {
      const top = stack.pop()
      if (!top || top.ch !== pairs[ch]) {
        return { char: ch, pos: i, kind: 'mismatch' }
      }
    }
  }
  if (stack.length > 0) {
    return { char: stack[stack.length - 1].ch, pos: stack[stack.length - 1].pos, kind: 'unclosed' }
  }
  return null
}

/** Aviator 校验：括号、${} 闭合、运算符合法、字符串引号闭合 */
function validateAviator(expr: string): ValidationResult {
  // 1. 字符串引号闭合
  const { stripped, unclosedQuote } = stripStrings(expr)
  if (unclosedQuote) {
    return { valid: false, error: `字符串引号 ${unclosedQuote} 未闭合` }
  }

  // 2. ${} 闭合检查：每个 $ 必须跟 {，且 { 必须有 } 闭合
  let i = 0
  while (i < stripped.length) {
    if (stripped[i] === '$') {
      if (stripped[i + 1] !== '{') {
        return { valid: false, error: `位置 ${i + 1}：'$' 后必须跟 '{'` }
      }
      const end = stripped.indexOf('}', i + 2)
      if (end === -1) {
        return { valid: false, error: `位置 ${i + 1}：'\${' 未闭合 '}'` }
      }
      i = end + 1
      continue
    }
    // 不允许孤立的 { 或 }（aviator 中 {} 仅用于 ${} 内）
    if (stripped[i] === '{' || stripped[i] === '}') {
      return { valid: false, error: `位置 ${i + 1}：Aviator 中 '{}' 仅用于 '\${变量名}'` }
    }
    i++
  }

  // 3. 括号匹配（仅 () 与 []，{} 已在上方处理）
  const noBraces = stripped.replace(/[{}]/g, ' ')
  const bracketErr = checkBrackets(noBraces)
  if (bracketErr) {
    if (bracketErr.kind === 'unclosed') {
      return { valid: false, error: `括号 '${bracketErr.char}' 未闭合` }
    }
    return { valid: false, error: `位置 ${bracketErr.pos + 1}：括号 '${bracketErr.char}' 不匹配` }
  }

  // 4. 运算符合法性检查：扫描 stripped 中的运算符字符，多字符运算符优先匹配
  const opChars = new Set(['+', '-', '*', '/', '%', '>', '<', '=', '!', '&', '|', '?', ':'])
  let j = 0
  while (j < stripped.length) {
    const ch = stripped[j]
    if (!opChars.has(ch)) {
      j++
      continue
    }
    // 尝试匹配 2 字符运算符
    const two = stripped.slice(j, j + 2)
    if (AVIATOR_OPERATORS.has(two)) {
      j += 2
      continue
    }
    // 单字符运算符
    if (AVIATOR_OPERATORS.has(ch)) {
      j += 1
      continue
    }
    // 不在合法集合中的运算符字符（如 @ # ~ ^ \ 以及单独的 = & |）
    return {
      valid: false,
      error: `位置 ${j + 1}：非法运算符 '${ch}'（Aviator 支持 + - * / % > < == != >= <= && || ! ? :）`
    }
  }

  return { valid: true }
}

/** Groovy 校验：括号、字符串引号闭合、连续运算符（** 除外） */
function validateGroovy(expr: string): ValidationResult {
  // 1. 字符串引号闭合（Groovy 还支持三引号字符串，简化版仅检查单/双引号）
  const { stripped, unclosedQuote } = stripStrings(expr)
  if (unclosedQuote) {
    return { valid: false, error: `字符串引号 ${unclosedQuote} 未闭合` }
  }

  // 2. 括号匹配（() [] {}）
  const bracketErr = checkBrackets(stripped)
  if (bracketErr) {
    if (bracketErr.kind === 'unclosed') {
      return { valid: false, error: `括号 '${bracketErr.char}' 未闭合` }
    }
    return { valid: false, error: `位置 ${bracketErr.pos + 1}：括号 '${bracketErr.char}' 不匹配` }
  }

  // 3. 连续运算符检查（** 除外；其它 3 个及以上相同运算符字符视为非法）
  //    采用「3 个及以上连续相同运算符字符」判定，避免对合法 2 字符运算符（== != >= <= && || ** ++ -- 等）误报
  const opChars = new Set(['+', '-', '*', '/', '%', '>', '<', '=', '!', '&', '|', '?', ':', '@', '~', '^'])
  let k = 0
  while (k < stripped.length) {
    const ch = stripped[k]
    if (!opChars.has(ch)) {
      k++
      continue
    }
    // 计算从 k 起，连续相同字符的长度
    let run = 1
    while (k + run < stripped.length && stripped[k + run] === ch) {
      run++
    }
    if (run >= 3) {
      return {
        valid: false,
        error: `位置 ${k + 1}：连续运算符 '${ch.repeat(run)}' 不合法`
      }
    }
    k += run
  }

  return { valid: true }
}

/** JavaScript 校验：用 new Function(expr) try-catch（仅语法解析，不执行） */
function validateJavaScript(expr: string): ValidationResult {
  try {
    // 用 Function 构造器解析表达式：包裹 return 让其作为函数体返回表达式结果
    // 仅解析，不调用，无副作用
    new Function(`return (${expr});`)
    return { valid: true }
  } catch (e: unknown) {
    // 提取 SyntaxError 信息（去掉浏览器附加的 "Function:" 等前缀）
    const msg = e instanceof Error ? e.message : String(e)
    return { valid: false, error: `JavaScript 语法错误：${msg}` }
  }
}

/**
 * 表达式语法校验入口。
 *
 * <p>按语言分发：aviator/groovy 走简化本地校验（括号/引号/运算符），
 * javascript 用 `new Function()` try-catch（仅解析不执行）。
 * 空表达式视为合法（无需校验）。</p>
 *
 * @param expr     表达式字符串
 * @param language 语言：aviator / groovy / javascript
 * @returns 校验结果
 */
function validateExpression(expr: string, language: ExpressionLanguage): ValidationResult {
  if (!expr || !expr.trim()) {
    return { valid: true }
  }
  if (language === 'aviator') return validateAviator(expr)
  if (language === 'groovy') return validateGroovy(expr)
  return validateJavaScript(expr)
}

// ===================== 实时校验（debounce 300ms） =====================

const isValid = ref(true)
const errorMessage = ref('')
let validateTimer: ReturnType<typeof setTimeout> | null = null

function runValidation() {
  const result = validateExpression(text.value, props.language)
  isValid.value = result.valid
  errorMessage.value = result.error ?? ''
}

watch(
  () => text.value,
  () => {
    if (validateTimer) clearTimeout(validateTimer)
    validateTimer = setTimeout(runValidation, 300)
  }
)

// 语言切换时立即重新校验
watch(
  () => props.language,
  () => {
    if (validateTimer) clearTimeout(validateTimer)
    runValidation()
  }
)

onBeforeUnmount(() => {
  if (validateTimer) clearTimeout(validateTimer)
})

// 初始校验
runValidation()
</script>

<template>
  <div class="expression-editor">
    <div class="ee-main" :style="{ height: `${height}px` }">
      <!-- 左侧：表达式文本框（透明文字 + pre 着色叠加） -->
      <div class="ee-editor">
        <pre ref="preRef" class="ee-highlight" aria-hidden="true"><span
          v-for="(t, i) in tokens"
          :key="i"
          :class="t.cls"
        >{{ t.text }}</span></pre>
        <textarea
          ref="textareaRef"
          class="ee-textarea"
          :value="text"
          placeholder="输入表达式，点击右侧变量/字段或底部函数插入"
          spellcheck="false"
          @input="onInput"
          @scroll="onScroll"
        ></textarea>
      </div>

      <!-- 右侧：变量/字段侧栏 -->
      <div class="ee-sidebar">
        <div class="ee-section">
          <div class="ee-section-title">变量</div>
          <div class="ee-chips">
            <span
              v-for="(v, i) in variables"
              :key="'v' + i"
              class="ee-chip"
              :title="`插入 ${varName(v)}`"
              @click="onVarClick(v)"
            >{{ varLabel(v) }}</span>
            <span v-if="variables.length === 0" class="ee-empty">无可用变量</span>
          </div>
        </div>
        <div class="ee-section">
          <div class="ee-section-title">字段</div>
          <div class="ee-chips">
            <span
              v-for="(f, i) in fields"
              :key="'f' + i"
              class="ee-chip"
              :title="`插入 ${f.name}`"
              @click="onFieldClick(f)"
            >{{ f.type ? `${f.name} (${f.type})` : f.name }}</span>
            <span v-if="fields.length === 0" class="ee-empty">无可用字段</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部：函数库提示 -->
    <div class="ee-functions">
      <div v-for="g in FUNCTION_LIBRARY" :key="g.key" class="ee-fn-group">
        <span class="ee-fn-group-label">{{ g.label }}:</span>
        <span
          v-for="item in g.items"
          :key="item.label"
          class="ee-fn-chip"
          :title="item.description"
          @click="onFuncClick(item)"
        >{{ item.label }}</span>
      </div>
    </div>

    <!-- 语法校验状态栏（实时校验，不阻断输入） -->
    <div
      class="ee-status"
      :class="{ 'ee-status-valid': isValid, 'ee-status-invalid': !isValid }"
      role="status"
      aria-live="polite"
    >
      <span v-if="isValid" class="ee-status-text">✓ 语法正确</span>
      <span v-else class="ee-status-text">✗ {{ errorMessage }}</span>
    </div>
  </div>
</template>

<style scoped>
.expression-editor {
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  background: var(--el-bg-color);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.ee-main {
  display: flex;
  min-height: 0;
}

/* ===== 编辑区：textarea 透明 + pre 着色叠加，二者字体/间距完全一致以保证对齐 ===== */
.ee-editor {
  position: relative;
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.ee-highlight,
.ee-textarea {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  margin: 0;
  padding: 8px 10px;
  border: 0;
  box-sizing: border-box;
  font-family: 'SF Mono', Menlo, Consolas, 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.5;
  letter-spacing: 0;
  white-space: pre-wrap;
  word-break: break-all;
  overflow: auto;
  tab-size: 2;
}

.ee-highlight {
  color: var(--el-text-color-primary);
  background: var(--el-bg-color);
  pointer-events: none;
  z-index: 1;
}

.ee-textarea {
  color: transparent;
  background: transparent;
  caret-color: var(--el-text-color-primary);
  resize: none;
  outline: none;
  z-index: 2;
}

.ee-textarea::placeholder {
  color: var(--el-text-color-placeholder);
}

/* token 着色 */
.ee-tok-var {
  color: #e96900;
}
.ee-tok-str {
  color: #42b983;
}
.ee-tok-num {
  color: #ae81ff;
}
.ee-tok-fn {
  color: #2973b7;
  font-weight: 600;
}
.ee-tok-id {
  color: var(--el-text-color-primary);
}
.ee-tok-plain {
  color: var(--el-text-color-secondary);
}

/* ===== 侧栏 ===== */
.ee-sidebar {
  width: 160px;
  flex-shrink: 0;
  border-left: 1px solid var(--el-border-color-lighter);
  padding: 8px;
  overflow: auto;
  background: var(--el-fill-color-blank);
}

.ee-section {
  margin-bottom: 10px;
}

.ee-section-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}

.ee-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.ee-chip {
  display: inline-block;
  padding: 2px 8px;
  font-size: 12px;
  font-family: 'SF Mono', Menlo, Consolas, 'Courier New', monospace;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: 3px;
  cursor: pointer;
  user-select: none;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ee-chip:hover {
  background: var(--el-color-primary-light-8);
}

.ee-empty {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

/* ===== 底部函数库 ===== */
.ee-functions {
  border-top: 1px solid var(--el-border-color-lighter);
  padding: 6px 8px;
  background: var(--el-fill-color-light);
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  align-items: center;
}

.ee-fn-group {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.ee-fn-group-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.ee-fn-chip {
  display: inline-block;
  padding: 1px 6px;
  font-size: 12px;
  font-family: 'SF Mono', Menlo, Consolas, 'Courier New', monospace;
  color: #2973b7;
  background: var(--el-fill-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 3px;
  cursor: pointer;
  user-select: none;
}

.ee-fn-chip:hover {
  background: var(--el-fill-color-dark);
}

/* ===== 语法校验状态栏 ===== */
.ee-status {
  border-top: 1px solid var(--el-border-color-lighter);
  padding: 4px 10px;
  font-size: 12px;
  line-height: 1.6;
  display: flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
  overflow: hidden;
}

.ee-status-valid {
  background: var(--el-color-success-light-9);
  color: var(--el-color-success);
}

.ee-status-invalid {
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}

.ee-status-text {
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
