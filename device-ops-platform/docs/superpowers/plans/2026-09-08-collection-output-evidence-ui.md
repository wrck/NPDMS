# Collection output evidence UI implementation

Approved scope: CollectionTaskPanel, SemanticResultPanel, RecordsManagementView, collection-evidence API/types/composable, session-export, transient-credentials, command-output-blocks, focused specs. Do not modify ProjectCollectionView, ParserSidebar, main.css, management.css, ScriptArtifactEditor. No browser/services/data reads/commits.

1. RED shared-panel specs: four tabs with semantic results owned internally; first semantic target expands once, polling preserves selection; compatibility facts partition by target; records readonly and no external result chain. GREEN implement scoped panels.
2. RED evidence recovery specs after coordinating backend DTO: independent loading, retry, abort/generation stale guards; AVAILABLE/UNAVAILABLE/RESTRICTED input; CAPTURED redacted submission vs historical execution facts. GREEN API/types/composable and panel integration without fabricated request snapshots.
3. RED submission/export specs: freeze submitted body and display from one snapshot; replace display with authoritative server evidence after acceptance; no secrets persisted or localStorage; BOM input export and explicit unavailable/restricted evidence. GREEN implement.
4. RED presentation regressions: neutral PARTIAL_SUCCESS and unknown legacy command status without mutating data. GREEN panel-local dark terminal pre, light facts/request, outer-only tab selectors and independent minimum height/scrolling.
5. Run focused Vitest and vue-tsc; report exact outcomes and remaining coordination blockers. No whole-repository formatting.

Parent contract: retain start(submission), stopPolling(), submit event, readonly prop. Parent/Sidebar remove result props and external SemanticResultPanel. Shared panel supplies its own results/details.
