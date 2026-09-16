-- Switch the existing menu without changing its URL, identity or permissions.
UPDATE system_menu
SET component = 'pms/delivery-business/site-survey/index',
    component_name = 'PmsSiteSurveyEntity', updater = 'entity-capability', update_time = NOW()
WHERE permission = 'pms:eng-site-survey:query' AND type = 2
  AND component = 'pms/engineering/site-survey/index' AND deleted = b'0';
