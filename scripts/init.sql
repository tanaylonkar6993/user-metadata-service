-- Initialisation script run once when the postgres container first starts.
-- Spring Boot / Hibernate will manage the schema via ddl-auto=update,
-- but we pre-create the database and grant permissions here.

GRANT ALL PRIVILEGES ON DATABASE userdb TO sre;
