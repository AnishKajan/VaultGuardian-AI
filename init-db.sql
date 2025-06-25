-- VaultGuardian AI Database Initialization Script
-- This script sets up the initial database schema and data

-- Create database if it doesn't exist (PostgreSQL will handle this via docker-compose)
-- The database 'vaultguardian' is created automatically by the postgres container

-- Create extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Users table (will be created by Hibernate, but we can add indexes)
-- Note: Hibernate will create the tables automatically with spring.jpa.hibernate.ddl-auto=update
-- This script is mainly for additional indexes, constraints, and initial data

-- Create indexes for better performance (after Hibernate creates tables)
-- These will be executed after table creation

-- Insert default admin user (password: admin123)
-- This will be executed after the application starts and creates tables
INSERT INTO users (username, email, password, first_name, last_name, is_enabled, is_account_non_expired, is_account_non_locked, is_credentials_non_expired, failed_login_attempts, created_at, updated_at) 
VALUES (
    'admin', 
    'admin@vaultguardian.com', 
    '$2a$12$LQv3c1yqBw17t3.BVTuGd.5nC8p.Z4.9U.HH8F/iXbVpPP0BPiDk6', -- password: admin123
    'System', 
    'Administrator', 
    true, 
    true, 
    true, 
    true, 
    0, 
    CURRENT_TIMESTAMP, 
    CURRENT_TIMESTAMP
) ON CONFLICT (username) DO NOTHING;

-- Insert admin role for the admin user
INSERT INTO user_roles (user_id, role) 
SELECT u.id, 'ADMIN' 
FROM users u 
WHERE u.username = 'admin' 
AND NOT EXISTS (
    SELECT 1 FROM user_roles ur 
    WHERE ur.user_id = u.id AND ur.role = 'ADMIN'
);

-- Insert default security officer user (password: security123)
INSERT INTO users (username, email, password, first_name, last_name, is_enabled, is_account_non_expired, is_account_non_locked, is_credentials_non_expired, failed_login_attempts, created_at, updated_at) 
VALUES (
    'security', 
    'security@vaultguardian.com', 
    '$2a$12$8l8mhWQ7lKvJ.CfZ4k9UF.J1Y3Lw2L8nDrJzNx5VbF8HgQlMpZtxu', -- password: security123
    'Security', 
    'Officer', 
    true, 
    true, 
    true, 
    true, 
    0, 
    CURRENT_TIMESTAMP, 
    CURRENT_TIMESTAMP
) ON CONFLICT (username) DO NOTHING;

-- Insert security officer role
INSERT INTO user_roles (user_id, role) 
SELECT u.id, 'SECURITY_OFFICER' 
FROM users u 
WHERE u.username = 'security' 
AND NOT EXISTS (
    SELECT 1 FROM user_roles ur 
    WHERE ur.user_id = u.id AND ur.role = 'SECURITY_OFFICER'
);

-- Create additional indexes for performance (these will run after Hibernate creates tables)
-- Note: These are conditional so they won't fail if tables don't exist yet

-- Add useful functions
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Add triggers for auto-updating updated_at columns
-- These will be added after the tables exist

-- Clean up old user sessions (optional maintenance)
CREATE OR REPLACE FUNCTION cleanup_old_sessions()
RETURNS void AS $$
BEGIN
    -- Reset failed login attempts for users locked more than 24 hours ago
    UPDATE users 
    SET failed_login_attempts = 0, 
        is_account_non_locked = true, 
        locked_at = NULL 
    WHERE locked_at < CURRENT_TIMESTAMP - INTERVAL '24 hours'
    AND is_account_non_locked = false;
END;
$$ language 'plpgsql';

-- Log successful initialization
DO $$
BEGIN
    RAISE NOTICE 'VaultGuardian database initialization completed successfully';
END $$;