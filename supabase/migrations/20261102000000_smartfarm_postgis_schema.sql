-- ============================================================================
-- SMARTFARM AURORA / VWING — SUPABASE POSTGRESQL + POSTGIS + RLS SCHEMA
-- Migration: 20261102000000_smartfarm_postgis_schema.sql
-- Tích hợp: PostgreSQL 16, PostGIS 3.4 (WGS84 & VN-2000), Row-Level Security,
--           Supabase Realtime Publication & Supabase Storage Buckets
-- ============================================================================

-- 1. Kích hoạt các Extension cốt lõi
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "postgis";

-- 2. Kiểu dữ liệu Enum cho phân quyền RBAC chuẩn VWing (5 vai trò)
DO $$ BEGIN
    CREATE TYPE public.sf_member_role AS ENUM ('owner', 'manager', 'worker', 'pilot', 'agronomist');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

-- 3. Bảng Hồ sơ Người dùng (Đồng bộ với auth.users của Supabase hoặc VWing Auth)
CREATE TABLE IF NOT EXISTS public.sf_users (
    id TEXT PRIMARY KEY DEFAULT encode(gen_random_bytes(8), 'hex'),
    auth_uid UUID UNIQUE REFERENCES auth.users(id) ON DELETE SET NULL,
    phone TEXT UNIQUE NOT NULL CHECK (phone ~ '^0[0-9]{9,10}$'),
    name TEXT NOT NULL CHECK (char_length(name) BETWEEN 1 AND 80),
    phone_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 4. Bảng Nông trại / Vườn (Hỗ trợ đa giác không gian PostGIS + Sổ đỏ VN-2000)
CREATE TABLE IF NOT EXISTS public.sf_gardens (
    garden_key TEXT PRIMARY KEY CHECK (garden_key ~ '^[A-Za-z0-9_-]{3,64}$'),
    name TEXT NOT NULL,
    owner_id TEXT REFERENCES public.sf_users(id) ON DELETE SET NULL,
    crop_type TEXT NOT NULL DEFAULT 'Sầu riêng',
    province TEXT DEFAULT 'Lâm Đồng',
    vn2000_meridian DOUBLE PRECISION DEFAULT 107.75,
    vn2000_sheet TEXT,
    vn2000_plot TEXT,
    area_ha DOUBLE PRECISION DEFAULT 1.8,
    perimeter_m DOUBLE PRECISION DEFAULT 542.0,
    boundary GEOMETRY(Polygon, 4326),
    centroid GEOMETRY(Point, 4326),
    rev INTEGER NOT NULL DEFAULT 0,
    price_per_kg NUMERIC(12, 2),
    price_region TEXT,
    public_config JSONB NOT NULL DEFAULT '{"enabled": false, "fields": [], "story": ""}'::jsonb,
    snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sf_gardens_boundary_gist ON public.sf_gardens USING GIST (boundary);
CREATE INDEX IF NOT EXISTS idx_sf_gardens_centroid_gist ON public.sf_gardens USING GIST (centroid);

-- 5. Bảng Thành viên & Phân quyền RBAC theo Vườn và Phạm vi Khu (Scope Zones)
CREATE TABLE IF NOT EXISTS public.sf_garden_members (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    garden_key TEXT NOT NULL REFERENCES public.sf_gardens(garden_key) ON DELETE CASCADE,
    user_id TEXT NOT NULL REFERENCES public.sf_users(id) ON DELETE CASCADE,
    role public.sf_member_role NOT NULL DEFAULT 'worker',
    scope_zones TEXT[] DEFAULT NULL, -- NULL = toàn vườn; mảng = chỉ các khu được cấp
    expires_at TIMESTAMPTZ DEFAULT NULL,
    revoked_at TIMESTAMPTZ DEFAULT NULL,
    invited_by TEXT,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (garden_key, user_id)
);

CREATE INDEX IF NOT EXISTS idx_sf_members_garden_user ON public.sf_garden_members (garden_key, user_id);

-- 6. Bảng Phân khu Canh tác (Khu A, B, C, D...) kèm Đa giác PostGIS & Địa hình DEM
CREATE TABLE IF NOT EXISTS public.sf_zones (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    garden_key TEXT NOT NULL REFERENCES public.sf_gardens(garden_key) ON DELETE CASCADE,
    zone_name TEXT NOT NULL,
    description TEXT DEFAULT '',
    polygon GEOMETRY(Polygon, 4326),
    elevation_m DOUBLE PRECISION,
    slope_deg DOUBLE PRECISION,
    mean_ndvi DOUBLE PRECISION,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (garden_key, zone_name)
);

CREATE INDEX IF NOT EXISTS idx_sf_zones_polygon_gist ON public.sf_zones USING GIST (polygon);

-- 7. Bảng Cây trồng Định danh (Tọa độ GPS thực địa, chỉ số viễn thám NDVI/NDMI, sản lượng)
CREATE TABLE IF NOT EXISTS public.sf_trees (
    id TEXT NOT NULL,
    garden_key TEXT NOT NULL REFERENCES public.sf_gardens(garden_key) ON DELETE CASCADE,
    tree_code TEXT NOT NULL,
    zone_name TEXT NOT NULL,
    variety TEXT DEFAULT 'Ri6',
    location GEOMETRY(Point, 4326),
    lat DOUBLE PRECISION,
    lng DOUBLE PRECISION,
    ndvi DOUBLE PRECISION DEFAULT 0.72,
    evi DOUBLE PRECISION DEFAULT 0.58,
    ndmi DOUBLE PRECISION DEFAULT 0.28,
    canopy_diameter_m DOUBLE PRECISION DEFAULT 6.2,
    fruits_count INTEGER DEFAULT 0,
    priority TEXT DEFAULT 'Ổn định',
    health_status TEXT DEFAULT 'Ổn định',
    note TEXT DEFAULT '',
    version INTEGER NOT NULL DEFAULT 1,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (garden_key, id)
);

CREATE INDEX IF NOT EXISTS idx_sf_trees_location_gist ON public.sf_trees USING GIST (location);
CREATE INDEX IF NOT EXISTS idx_sf_trees_garden_zone ON public.sf_trees (garden_key, zone_name);

-- 8. Bảng Chuỗi thời gian Cảm biến IoT & Viễn thám Vệ tinh (Time-Series Telemetry)
CREATE TABLE IF NOT EXISTS public.sf_iot_telemetry (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    garden_key TEXT NOT NULL REFERENCES public.sf_gardens(garden_key) ON DELETE CASCADE,
    zone_name TEXT DEFAULT 'ALL',
    soil_moisture_pct DOUBLE PRECISION,
    soil_ph DOUBLE PRECISION,
    soil_ec_dsm DOUBLE PRECISION,
    nitrogen_mgkg DOUBLE PRECISION,
    phosphorus_mgkg DOUBLE PRECISION,
    potassium_mgkg DOUBLE PRECISION,
    air_temp_c DOUBLE PRECISION,
    air_humidity_pct DOUBLE PRECISION,
    rain_prob_pct DOUBLE PRECISION,
    ndvi DOUBLE PRECISION,
    evi DOUBLE PRECISION,
    ndre DOUBLE PRECISION,
    ndmi DOUBLE PRECISION,
    source TEXT NOT NULL DEFAULT 'iot-gateway', -- 'iot-gateway' | 'gee-sentinel2' | 'open-meteo'
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sf_telemetry_garden_time ON public.sf_iot_telemetry (garden_key, recorded_at DESC);

-- 9. Bảng Nhật ký Canh tác, Vấn đề Sâu bệnh & Công việc Thực địa
CREATE TABLE IF NOT EXISTS public.sf_care_logs (
    id TEXT NOT NULL,
    garden_key TEXT NOT NULL REFERENCES public.sf_gardens(garden_key) ON DELETE CASCADE,
    collection_type TEXT NOT NULL CHECK (collection_type IN ('events', 'issues', 'tasks', 'sales', 'droneFlights', 'measures')),
    scope_zone TEXT,
    tree_ids TEXT[] DEFAULT '{}',
    title TEXT,
    status TEXT,
    assignee TEXT,
    done BOOLEAN DEFAULT FALSE,
    cost_amount NUMERIC(14, 2),
    photo_urls TEXT[] DEFAULT '{}',
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    version INTEGER NOT NULL DEFAULT 1,
    created_by TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (garden_key, collection_type, id)
);

-- 10. Bảng Nhật ký Kiểm toán Bất biến (Immutable Audit Trail)
CREATE TABLE IF NOT EXISTS public.sf_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    garden_key TEXT NOT NULL REFERENCES public.sf_gardens(garden_key) ON DELETE CASCADE,
    actor_id TEXT,
    actor_name TEXT NOT NULL,
    actor_role TEXT NOT NULL,
    action TEXT NOT NULL,
    detail JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sf_audit_garden_time ON public.sf_audit_logs (garden_key, created_at DESC);

-- ============================================================================
-- HÀM KHÔNG GIAN POSTGIS (SPATIAL RPC FUNCTIONS)
-- ============================================================================

-- Tính diện tích (m2 & ha) và chu vi (m) thực tế của đa giác lô đất theo hệ chiếu mét UTM/VN-2000
CREATE OR REPLACE FUNCTION public.sf_calculate_plot_metrics(geojson_polygon TEXT)
RETURNS TABLE (area_m2 DOUBLE PRECISION, area_ha DOUBLE PRECISION, perimeter_m DOUBLE PRECISION, centroid_lat DOUBLE PRECISION, centroid_lng DOUBLE PRECISION)
LANGUAGE plpgsql STABLE AS $$
DECLARE
    geom GEOMETRY;
    centroid_pt GEOMETRY;
BEGIN
    geom := ST_SetSRID(ST_GeomFromGeoJSON(geojson_polygon), 4326);
    centroid_pt := ST_Centroid(geom);
    RETURN QUERY SELECT
        ROUND((ST_Area(geom::geography))::numeric, 2)::double precision AS area_m2,
        ROUND((ST_Area(geom::geography) / 10000.0)::numeric, 4)::double precision AS area_ha,
        ROUND((ST_Perimeter(geom::geography))::numeric, 2)::double precision AS perimeter_m,
        ST_Y(centroid_pt)::double precision AS centroid_lat,
        ST_X(centroid_pt)::double precision AS centroid_lng;
END;
$$;

-- Tìm các cây trồng cần kiểm tra (NDVI thấp) nằm trong bán kính (mét) từ vị trí GPS của nhân viên đi tuần
CREATE OR REPLACE FUNCTION public.sf_nearby_stressed_trees(
    p_garden_key TEXT,
    p_lat DOUBLE PRECISION,
    p_lng DOUBLE PRECISION,
    p_radius_m DOUBLE PRECISION DEFAULT 500.0,
    p_max_ndvi DOUBLE PRECISION DEFAULT 0.60
)
RETURNS TABLE (
    tree_id TEXT,
    tree_code TEXT,
    zone_name TEXT,
    ndvi DOUBLE PRECISION,
    health_status TEXT,
    distance_m DOUBLE PRECISION
)
LANGUAGE sql STABLE AS $$
    SELECT
        t.id AS tree_id,
        t.tree_code,
        t.zone_name,
        t.ndvi,
        t.health_status,
        ROUND(ST_Distance(
            t.location::geography,
            ST_SetSRID(ST_MakePoint(p_lng, p_lat), 4326)::geography
        )::numeric, 1)::double precision AS distance_m
    FROM public.sf_trees t
    WHERE t.garden_key = p_garden_key
      AND t.ndvi <= p_max_ndvi
      AND t.location IS NOT NULL
      AND ST_DWithin(
          t.location::geography,
          ST_SetSRID(ST_MakePoint(p_lng, p_lat), 4326)::geography,
          p_radius_m
      )
    ORDER BY t.ndvi ASC, distance_m ASC;
$$;

-- ============================================================================
-- ROW LEVEL SECURITY (RLS) — BẢO MẬT CẤP DÒNG DỮ LIỆU THEO VAI TRÒ & PHẠM VI KHU
-- ============================================================================
ALTER TABLE public.sf_users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sf_gardens ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sf_garden_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sf_zones ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sf_trees ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sf_iot_telemetry ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sf_care_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sf_audit_logs ENABLE ROW LEVEL SECURITY;

-- Hàm kiểm tra quyền thành viên đang hoạt động trên vườn
CREATE OR REPLACE FUNCTION public.sf_has_garden_access(p_garden_key TEXT, p_allowed_roles public.sf_member_role[] DEFAULT NULL)
RETURNS BOOLEAN
LANGUAGE sql SECURITY DEFINER STABLE AS $$
    SELECT EXISTS (
        SELECT 1
        FROM public.sf_garden_members m
        JOIN public.sf_users u ON u.id = m.user_id
        WHERE m.garden_key = p_garden_key
          AND u.auth_uid = auth.uid()
          AND m.revoked_at IS NULL
          AND (m.expires_at IS NULL OR m.expires_at > NOW())
          AND (p_allowed_roles IS NULL OR m.role = ANY(p_allowed_roles))
    );
$$;

-- Chính sách đọc vườn: Thành viên hợp lệ hoặc trang truy xuất QR công khai (khi public_config.enabled = true)
DROP POLICY IF EXISTS "sf_gardens_select_policy" ON public.sf_gardens;
CREATE POLICY "sf_gardens_select_policy" ON public.sf_gardens
    FOR SELECT USING (
        (public_config->>'enabled')::boolean = TRUE
        OR public.sf_has_garden_access(garden_key, NULL)
    );

-- Chính sách cập nhật vườn: Chỉ Chủ vườn (owner) hoặc Quản lý (manager)
DROP POLICY IF EXISTS "sf_gardens_update_policy" ON public.sf_gardens;
CREATE POLICY "sf_gardens_update_policy" ON public.sf_gardens
    FOR UPDATE USING (
        public.sf_has_garden_access(garden_key, ARRAY['owner', 'manager']::public.sf_member_role[])
    );

-- Chính sách đọc Cây trồng theo Phạm vi Khu (Scope Zones)
DROP POLICY IF EXISTS "sf_trees_select_policy" ON public.sf_trees;
CREATE POLICY "sf_trees_select_policy" ON public.sf_trees
    FOR SELECT USING (
        EXISTS (
            SELECT 1
            FROM public.sf_garden_members m
            JOIN public.sf_users u ON u.id = m.user_id
            WHERE m.garden_key = sf_trees.garden_key
              AND u.auth_uid = auth.uid()
              AND m.revoked_at IS NULL
              AND (m.expires_at IS NULL OR m.expires_at > NOW())
              AND (m.scope_zones IS NULL OR sf_trees.zone_name = ANY(m.scope_zones))
        )
    );

-- Chính sách đọc Telemetry IoT
DROP POLICY IF EXISTS "sf_telemetry_select_policy" ON public.sf_iot_telemetry;
CREATE POLICY "sf_telemetry_select_policy" ON public.sf_iot_telemetry
    FOR SELECT USING (public.sf_has_garden_access(garden_key, NULL));

-- ============================================================================
-- SUPABASE REALTIME PUBLICATION (ĐỒNG BỘ THỜI GIAN THỰC WEBSOCKET)
-- ============================================================================
DO $$ BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.sf_gardens;
EXCEPTION WHEN OTHERS THEN NULL; END $$;

DO $$ BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.sf_trees;
EXCEPTION WHEN OTHERS THEN NULL; END $$;

DO $$ BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.sf_iot_telemetry;
EXCEPTION WHEN OTHERS THEN NULL; END $$;

DO $$ BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.sf_care_logs;
EXCEPTION WHEN OTHERS THEN NULL; END $$;

-- ============================================================================
-- SUPABASE STORAGE BUCKETS (ẢNH THỰC ĐỊA, DRONE, SỔ ĐỎ, PANORAMA 360)
-- ============================================================================
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES
    ('garden-photos', 'garden-photos', true, 31457280, ARRAY['image/jpeg', 'image/png', 'image/webp']),
    ('land-certificates', 'land-certificates', false, 20971520, ARRAY['image/jpeg', 'image/png', 'application/pdf']),
    ('panorama-360', 'panorama-360', true, 52428800, ARRAY['image/jpeg', 'image/webp'])
ON CONFLICT (id) DO NOTHING;
