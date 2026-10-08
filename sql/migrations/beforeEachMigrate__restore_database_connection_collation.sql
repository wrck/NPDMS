-- Restore connection comparison semantics to the selected database before each migration.
-- Earlier immutable migrations execute SET NAMES utf8mb4, which can reset a MySQL 8
-- connection to utf8mb4_0900_ai_ci even when the database uses utf8mb4_unicode_ci.
-- CAST(... AS CHAR) then conflicts with existing unicode_ci columns in V374.
-- This changes only the Flyway session: no table/global collation, historical SQL,
-- migration checksum, or schema-history entry is rewritten.
SET SESSION collation_connection = @@collation_database;
