/** Original entity commands retain their page action names. */
export const requirementAnalysisOperationActions: Readonly<Record<string, readonly string[]>> = {
  'SOL.REQUIREMENT_ANALYSIS.CREATE': ['CREATE', 'CREATE_INITIAL_DRAFT'],
  'SOL.REQUIREMENT_ANALYSIS.SAVE': ['PATCH_FORM'],
  'SOL.REQUIREMENT_ANALYSIS.COMPLETE': ['COMPLETE'],
  'SOL.REQUIREMENT_ANALYSIS.COPY': ['CREATE_DRAFT']
}
