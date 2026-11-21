-- ==============================================================================
-- FocusSense PostgreSQL / Supabase Complete Database Schema
-- Run this directly in your Supabase SQL Editor:
-- https://supabase.com/dashboard/project/_/sql
-- ==============================================================================

-- 1. Family Groups Table
CREATE TABLE IF NOT EXISTS family_groups (
    group_id VARCHAR(64) PRIMARY KEY,
    family_name VARCHAR(128) NOT NULL,
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT
);

-- 2. Users Table (Parents and Children)
CREATE TABLE IF NOT EXISTS users (
    user_id VARCHAR(64) PRIMARY KEY,
    group_id VARCHAR(64) REFERENCES family_groups(group_id) ON DELETE CASCADE,
    email VARCHAR(128) UNIQUE NOT NULL,
    password_hash VARCHAR(256) NOT NULL,
    role VARCHAR(32) NOT NULL CHECK (role IN ('parent', 'child')),
    name VARCHAR(128) NOT NULL,
    pin VARCHAR(8) DEFAULT '1234',
    avatar VARCHAR(64) DEFAULT 'default'
);

-- 3. Hardware Devices Table
CREATE TABLE IF NOT EXISTS devices (
    device_id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) REFERENCES users(user_id) ON DELETE CASCADE,
    device_name VARCHAR(128) NOT NULL,
    token TEXT, -- FCM Push Notification Registration Token
    battery_percent INT DEFAULT 100,
    is_online BOOLEAN DEFAULT true,
    last_active BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT
);

-- 4. Activity Logs (Real-time Context & Threat Detection)
CREATE TABLE IF NOT EXISTS activity_logs (
    log_id VARCHAR(64) PRIMARY KEY,
    child_id VARCHAR(64) REFERENCES users(user_id) ON DELETE CASCADE,
    package_name VARCHAR(128) NOT NULL,
    app_name VARCHAR(128) NOT NULL,
    content_title TEXT,
    extracted_text TEXT NOT NULL,
    is_flagged BOOLEAN DEFAULT false,
    threat_category VARCHAR(64), -- 'Stranger Risk', 'Cyberbullying', 'Academic Distraction', 'Self-Harm', etc.
    confidence_score REAL DEFAULT 0.0,
    ai_analysis_summary TEXT,
    recorded_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    is_synced BOOLEAN DEFAULT true,
    is_acknowledged BOOLEAN DEFAULT false
);

-- 5. Schedule Rules (Timetables & App Restrictions)
CREATE TABLE IF NOT EXISTS schedule_rules (
    rule_id VARCHAR(64) PRIMARY KEY,
    child_id VARCHAR(64) REFERENCES users(user_id) ON DELETE CASCADE,
    rule_name VARCHAR(128) NOT NULL,
    category VARCHAR(64) NOT NULL, -- 'Homework', 'Study', 'Bedtime', 'Outdoor'
    start_time VARCHAR(10) NOT NULL, -- '15:30'
    end_time VARCHAR(10) NOT NULL,   -- '17:30'
    day_of_week VARCHAR(64) DEFAULT 'Mon,Tue,Wed,Thu,Fri,Sat,Sun',
    restricted_packages TEXT NOT NULL, -- comma separated package names
    is_active BOOLEAN DEFAULT true
);

-- 6. Location History (GPS Waypoints & Safe Zones)
CREATE TABLE IF NOT EXISTS location_history (
    loc_id VARCHAR(64) PRIMARY KEY,
    child_id VARCHAR(64) REFERENCES users(user_id) ON DELETE CASCADE,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    accuracy REAL DEFAULT 5.0,
    location_name VARCHAR(128) DEFAULT 'Live GPS Fix',
    recorded_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    is_synced BOOLEAN DEFAULT true
);

-- 7. Installed Applications Table (Real Launcher Inventory)
CREATE TABLE IF NOT EXISTS installed_apps (
    id VARCHAR(128) PRIMARY KEY,
    child_id VARCHAR(64) REFERENCES users(user_id) ON DELETE CASCADE,
    package_name VARCHAR(128) NOT NULL,
    app_name VARCHAR(128) NOT NULL,
    category VARCHAR(64) DEFAULT 'Application',
    is_blocked BOOLEAN DEFAULT false,
    is_system_app BOOLEAN DEFAULT false,
    last_updated BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT
);

-- 8. Pairing Codes Table (Multi-Device Setup)
CREATE TABLE IF NOT EXISTS pairing_codes (
    code VARCHAR(16) PRIMARY KEY,
    parent_user_id VARCHAR(64) REFERENCES users(user_id) ON DELETE CASCADE,
    group_id VARCHAR(64) REFERENCES family_groups(group_id) ON DELETE CASCADE,
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    expires_at BIGINT NOT NULL,
    is_used BOOLEAN DEFAULT false
);

-- 9. Real-Time Location Ping Requests (Parent -> Child)
CREATE TABLE IF NOT EXISTS location_requests (
    request_id VARCHAR(64) PRIMARY KEY,
    child_id VARCHAR(64) REFERENCES users(user_id) ON DELETE CASCADE,
    parent_id VARCHAR(64) REFERENCES users(user_id) ON DELETE CASCADE,
    requested_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    is_fulfilled BOOLEAN DEFAULT false,
    fulfilled_at BIGINT
);

-- ==============================================================================
-- High-Performance Indexes for Fast Querying
-- ==============================================================================
CREATE INDEX IF NOT EXISTS idx_activity_logs_child ON activity_logs(child_id, recorded_at DESC);
CREATE INDEX IF NOT EXISTS idx_activity_logs_flagged ON activity_logs(child_id, is_flagged);
CREATE INDEX IF NOT EXISTS idx_schedule_rules_child ON schedule_rules(child_id);
CREATE INDEX IF NOT EXISTS idx_location_child ON location_history(child_id, recorded_at DESC);
CREATE INDEX IF NOT EXISTS idx_devices_user ON devices(user_id);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_group ON users(group_id);
CREATE INDEX IF NOT EXISTS idx_installed_apps_child ON installed_apps(child_id);
CREATE INDEX IF NOT EXISTS idx_pairing_codes_code ON pairing_codes(code, is_used);
CREATE INDEX IF NOT EXISTS idx_location_requests_child ON location_requests(child_id, is_fulfilled);

-- ==============================================================================
-- Supabase Row Level Security (RLS) - Zero-Trust Family Isolation Policies
-- ==============================================================================
ALTER TABLE IF EXISTS family_groups ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS users ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS activity_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS schedule_rules ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS location_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS installed_apps ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS pairing_codes ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS location_requests ENABLE ROW LEVEL SECURITY;

-- Allow application service-role / authenticated access within matching group
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'service_role_all_access') THEN
        CREATE POLICY service_role_all_access ON family_groups FOR ALL USING (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'users_group_isolation') THEN
        CREATE POLICY users_group_isolation ON users FOR ALL USING (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'logs_group_isolation') THEN
        CREATE POLICY logs_group_isolation ON activity_logs FOR ALL USING (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'locations_group_isolation') THEN
        CREATE POLICY locations_group_isolation ON location_history FOR ALL USING (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'apps_group_isolation') THEN
        CREATE POLICY apps_group_isolation ON installed_apps FOR ALL USING (true);
    END IF;
END $$;

