/** Draft creation/update share the existing editor action; server operations remain distinct. */
export const acceptanceReportOperationActions: Readonly<Record<string, readonly string[]>> = {
  'ACC.ACCEPTANCE_REPORT.CREATE_DRAFT': ['UPDATE'],
  'ACC.ACCEPTANCE_REPORT.UPDATE_DRAFT': ['UPDATE'],
  'ACC.ACCEPTANCE_REPORT.PUBLISH': ['PUBLISH'], 'ACC.ACCEPTANCE_REPORT.REVOKE': ['REVOKE']
}
