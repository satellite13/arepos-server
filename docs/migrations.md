# Database migrations

The applied `001-initial-schema` changeset and its `001-init.sql` file are frozen.
Its `runOnChange` flag was removed to prevent accidental re-execution against existing databases.

All schema changes must be added as new numbered changesets, starting with `040`.
