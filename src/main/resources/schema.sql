-- Enable PostgreSQL extensions required by the entity mappings.
-- Several email, college-domain, and skill-name columns use citext for
-- case-insensitive comparisons. Neon does not enable extensions by default.
CREATE EXTENSION IF NOT EXISTS citext;
