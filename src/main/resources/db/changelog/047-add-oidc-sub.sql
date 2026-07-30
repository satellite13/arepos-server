ALTER TABLE users ADD COLUMN oidc_sub VARCHAR(255);
CREATE UNIQUE INDEX uq_users_oidc_sub ON users (oidc_sub) WHERE oidc_sub IS NOT NULL;
