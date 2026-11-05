CREATE TABLE IF NOT EXISTS demo_sessions (
    id UUID PRIMARY KEY,
    state_json TEXT NOT NULL,
    last_access TIMESTAMP WITH TIME ZONE NOT NULL
);
