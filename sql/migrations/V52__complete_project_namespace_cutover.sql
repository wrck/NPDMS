-- F-PROJ-001 / PM-01 / PM-03
-- Complete the direct namespace cutover for active project-owned tables.

RENAME TABLE
    pms_project_task_dependency TO proj_project_task_dependency,
    pms_project_tree_change_batch TO proj_project_tree_change_batch,
    pms_project_phase_template TO proj_project_phase_template,
    pms_project_phase TO proj_project_phase,
    pms_project_risk TO proj_project_risk,
    pms_project_governance_action TO proj_project_governance_action,
    pms_project_portfolio TO proj_project_portfolio,
    pms_project_portfolio_member TO proj_project_portfolio_member,
    pms_project_portfolio_rule TO proj_project_portfolio_rule;
