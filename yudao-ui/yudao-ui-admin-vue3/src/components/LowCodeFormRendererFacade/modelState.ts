/** V1 增量回填语义：保留未提供的字段，且不修改调用方的数据对象。 */
export function mergeRendererModel(
  current: Record<string, unknown>,
  patch?: Record<string, unknown>
): Record<string, unknown> {
  if (!patch || Object.keys(patch).every((key) => key in current && current[key] === patch[key])) {
    return current
  }
  return { ...current, ...patch }
}
