/** Owner page actions, independent of project permissions and result completion. */
export const siteSurveyOperationActions: Readonly<Record<string, readonly string[]>> = {
  'SOL.SITE_SURVEY.CREATE': ['CREATE'], 'SOL.SITE_SURVEY.UPDATE': ['UPDATE'],
  'SOL.SITE_SURVEY.DELETE': ['DELETE'], 'SOL.SITE_SURVEY.CONFIRM': ['CONFIRM'],
  'SOL.SITE_SURVEY.REJECT': ['REJECT'], 'SOL.SITE_SURVEY.ARCHIVE': ['ARCHIVE']
}
