/**
 * 表达式编辑器绑定树数据结构（借鉴 Budibase Bindings Drawer）。
 *
 * <p>从外部传入的 fields / variables 列表构建一棵可搜索的绑定类别树，
 * 供编辑器左侧侧栏展示与点击插入。每棵树节点携带 insertText——点击或选中
 * 后通过 editor.executeEdits 插入到光标位置。</p>
 *
 * <p>除字段/变量外，还内置 math/string/date 三类常用函数库，作为第三棵子树。</p>
 */
export interface BindingNode {
  /** 节点唯一 key */
  key: string
  /** 显示标签 */
  label: string
  /** 值类型（字段/变量的数据类型，函数节点缺省） */
  type?: string
  /** 节点描述（tooltip） */
  description?: string
  /** 子节点（类别节点携带） */
  children?: BindingNode[]
  /** 点击插入到编辑器光标位置的文本（叶子节点携带） */
  insertText?: string
}

/** 字段/变量项结构 */
export interface BindingItem {
  name: string
  type: string
}

/** 内置函数库定义 */
const FUNCTION_LIBRARY: BindingNode[] = [
  {
    key: 'math',
    label: '数学函数',
    children: [
      { key: 'abs', label: 'abs(x)', description: '绝对值', insertText: 'abs(${1:x})' },
      { key: 'ceil', label: 'ceil(x)', description: '向上取整', insertText: 'ceil(${1:x})' },
      { key: 'floor', label: 'floor(x)', description: '向下取整', insertText: 'floor(${1:x})' },
      { key: 'round', label: 'round(x)', description: '四舍五入', insertText: 'round(${1:x})' }
    ]
  },
  {
    key: 'string',
    label: '字符串函数',
    children: [
      { key: 'length', label: 'length(s)', description: '字符串长度', insertText: 'length(${1:s})' },
      { key: 'substring', label: 'substring(s, start, end)', description: '截取子串', insertText: 'substring(${1:s}, ${2:start}, ${3:end})' },
      { key: 'toUpperCase', label: 'toUpperCase(s)', description: '转大写', insertText: 'toUpperCase(${1:s})' }
    ]
  },
  {
    key: 'date',
    label: '日期函数',
    children: [
      { key: 'now', label: 'now()', description: '当前时间戳', insertText: 'now()' },
      { key: 'formatDate', label: 'formatDate(date, pattern)', description: '按指定格式格式化日期', insertText: 'formatDate(${1:date}, ${2:"yyyy-MM-dd"})' }
    ]
  }
]

/**
 * 从 fields / variables 构建绑定树。
 *
 * <p>返回三棵子树：字段（fields.xxx）、变量（vars.xxx）、函数库。
 * 叶子节点的 insertText 为可直接插入的表达式片段。</p>
 */
export function buildBindingTree(
  fields: BindingItem[] = [],
  variables: BindingItem[] = []
): BindingNode[] {
  const tree: BindingNode[] = []

  tree.push({
    key: 'fields',
    label: '字段',
    description: '表单字段，引用形式 fields.字段名',
    children: fields.map((f) => ({
      key: `fields.${f.name}`,
      label: `${f.name}${f.type ? ` (${f.type})` : ''}`,
      type: f.type,
      insertText: `fields.${f.name}`
    }))
  })

  tree.push({
    key: 'vars',
    label: '变量',
    description: '上下文变量，引用形式 vars.变量名',
    children: variables.map((v) => ({
      key: `vars.${v.name}`,
      label: `${v.name}${v.type ? ` (${v.type})` : ''}`,
      type: v.type,
      insertText: `vars.${v.name}`
    }))
  })

  tree.push({
    key: 'functions',
    label: '函数库',
    description: '内置 math / string / date 常用函数',
    children: FUNCTION_LIBRARY
  })

  return tree
}

/** 拍平绑定树为可补全的叶子项列表（按所属前缀分组） */
export interface FlatBinding {
  prefix: 'fields' | 'vars' | ''
  name: string
  type?: string
  insertText: string
  detail: string
}

export function flattenBindings(tree: BindingNode[]): FlatBinding[] {
  const result: FlatBinding[] = []
  for (const node of tree) {
    if (!node.children) continue
    const prefix: FlatBinding['prefix'] = node.key === 'fields' ? 'fields' : node.key === 'vars' ? 'vars' : ''
    for (const child of node.children) {
      if (child.insertText) {
        result.push({
          prefix,
          name: child.label,
          type: child.type,
          insertText: child.insertText,
          detail: child.description || ''
        })
      }
    }
  }
  return result
}
