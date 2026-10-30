export type SurveyPreparationBranch = { title: string; when: Record<string, string | number | boolean> }

/** Display-only routing hints, using the form's configured field bindings and values. */
export const surveyPreparationBranches = (
  branches: SurveyPreparationBranch[],
  values: Record<string, unknown>
) => branches.map(branch => {
  const conditions = Object.entries(branch.when)
  const configured = conditions.length > 0 && conditions.every(([, expected]) =>
    ['boolean', 'number', 'string'].includes(typeof expected))
  const missing = !configured || conditions.some(([field]) => values[field] == null || values[field] === '')
  return { title: branch.title, state: missing ? 'UNKNOWN' :
    conditions.every(([field, expected]) => values[field] === expected) ? 'MATCHED' : 'NOT_MATCHED' }
})
