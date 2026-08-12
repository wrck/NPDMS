SELECT version, description, success, installed_on FROM flyway_schema_history WHERE version IN ('27','28') ORDER BY installed_rank;
