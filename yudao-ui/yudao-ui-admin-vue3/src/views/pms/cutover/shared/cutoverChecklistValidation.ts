import type { CutoverChecklistItem, CutoverExternalItemState } from '@/api/pms/cutover'

// Navigation after a server rejection only; submission rules remain on the server.
export const findChecklistValidationItem = ({ items, message, externalItems = [] }: {
  items: CutoverChecklistItem[]
  message: string
  externalItems?: CutoverExternalItemState[]
}) => {
  const applicable = items.filter(item => item.applicable)
  const named = applicable.filter(item => message.startsWith(`${item.itemName}：`)
    || message.startsWith(`${item.itemName} / `))
    .sort((a, b) => b.itemName.length - a.itemName.length)[0]
  if (named) return named
  if (message !== '必填清单项尚未完成') return undefined
  return applicable.find(item => {
    if (!item.required) return false
    const result = item.currentResult
    if (!result) return true
    if (result.resultSourceCode === 'COLLECTION') {
      if (result.loadFailureCode || !result.collectionResultReferenceId || !result.collectionResultVersion) return true
      // The result read contract includes analysis only after a conclusion has been saved.
      try { return JSON.parse(result.answerSnapshot)?.analysis === undefined } catch { return false }
    }
    return result.resultSourceCode === 'EXTERNAL' && (!!result.loadFailureCode
      || externalItems.find(state => state.stableItemKey === item.stableItemKey)?.feedbackSaved === false)
  })
}

export const focusChecklistValidationItem = (root: HTMLElement, stableItemKey: string) => {
  const target = Array.from(root.querySelectorAll<HTMLElement>('[data-checklist-item-key]'))
    .find(element => element.dataset.checklistItemKey === stableItemKey)
  if (!target) return
  target.querySelectorAll('details').forEach(details => { details.open = true })
  target.scrollIntoView({ block: 'start' })
  const input = target.querySelector<HTMLElement>(
    'input:not(:disabled):not([type="hidden"]), textarea:not(:disabled), [role="combobox"]:not([aria-disabled="true"]), button:not(:disabled)'
  )
  const focusTarget = input || target
  focusTarget.focus({ preventScroll: true })
}
