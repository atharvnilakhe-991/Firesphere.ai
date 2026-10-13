-- =============================================================================
-- Repeatable Migration: R__01_seed_reference_data.sql
-- Description: Standard reference taxonomy, system roles, permissions, and demo tenant
-- Author: Member 3 (Database, Infrastructure & DevOps)
-- =============================================================================

-- 1. Default Reference Municipal Tenant Organization
INSERT INTO firesphere.organizations (id, code, name, type, jurisdiction_area, contact_email, status)
VALUES (1, 'PMC-FIRE', 'Pune Municipal Corporation Fire Brigade', 'MUNICIPAL', 'Pune Metropolitan Region', 'fire.control@punecorporation.org', 'ACTIVE')
ON CONFLICT (code) DO UPDATE 
SET name = EXCLUDED.name,
    status = EXCLUDED.status;

SELECT setval('firesphere.organizations_id_seq', GREATEST(1, (SELECT MAX(id) FROM firesphere.organizations)));

-- 2. Core System Roles
INSERT INTO firesphere.roles (org_id, role_code, role_name, description, is_system)
VALUES 
    (NULL, 'SUPER_ADMIN', 'Platform Super Administrator', 'Full platform operational access', TRUE),
    (1, 'FIRE_CHIEF', 'Chief Fire Officer (CFO)', 'Head of municipal fire department with final NOC authority', FALSE),
    (1, 'STATION_OFFICER', 'Station Officer (SO)', 'In-charge of fire station turnout and apparatus readiness', FALSE),
    (1, 'FIELD_INSPECTOR', 'Fire Safety Inspector', 'Conducts life-safety building audits and NOC inspections', FALSE),
    (1, 'DISPATCHER_112', 'Emergency Call Dispatcher', 'Handles 112/911 call intake and apparatus dispatch', FALSE)
ON CONFLICT (org_id, role_code) DO NOTHING;

-- 3. Core System Permissions
INSERT INTO firesphere.permissions (permission_code, module, description)
VALUES 
    ('BUILDING_VIEW', 'BUILDINGS', 'View building safety records and GIS footprints'),
    ('BUILDING_CREATE', 'BUILDINGS', 'Register new building entity'),
    ('BUILDING_UPDATE', 'BUILDINGS', 'Modify building safety parameters'),
    ('NOC_APPLY', 'FIRE_NOC', 'Submit application for Fire No-Objection Certificate'),
    ('NOC_INSPECT', 'FIRE_NOC', 'Conduct statutory inspection for NOC approval'),
    ('NOC_APPROVE', 'FIRE_NOC', 'Statutory authority sign-off and certificate issuance'),
    ('INCIDENT_DISPATCH', 'EMERGENCY', 'Initiate multi-apparatus turnout for 112 emergency'),
    ('INCIDENT_UPDATE_TIMELINE', 'EMERGENCY', 'Log tactical progress and water usage on-scene'),
    ('AUDIT_LOG_VIEW', 'SECURITY', 'Inspect immutable CDC security audit logs')
ON CONFLICT (permission_code) DO NOTHING;

-- 4. Standard NOC Application Categories (From Verified Legacy Table 34)
INSERT INTO firesphere.noc_application_categories (category_code, category_name, standard_fee, validity_years, description)
VALUES 
    ('PROVISIONAL_NOC', 'Provisional Fire NOC (Pre-Construction)', 5000.00, 2, 'Approval of architectural fire safety schematics before construction'),
    ('FINAL_OCCUPANCY_NOC', 'Final Fire NOC (Occupancy Certificate)', 10000.00, 1, 'Verification of installed fire protection systems prior to building occupation'),
    ('ANNUAL_RENEWAL', 'Annual Fire NOC Renewal', 3000.00, 1, 'Periodic statutory audit and certification renewal')
ON CONFLICT (category_code) DO NOTHING;

-- 5. Standard Inspection Checklists (From Verified Legacy Table 26)
INSERT INTO firesphere.inspection_checklists (id, org_id, checklist_code, title, occupancy_type, nbc_reference_clause, is_active)
VALUES 
    (1, 1, 'NBC_HIGH_RISE_COMMERCIAL', 'National Building Code High-Rise Commercial Audit', 'COMMERCIAL', 'Part 4 - Fire & Life Safety (Clause 4.3)', TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO firesphere.inspection_checklist_items (checklist_id, item_sequence, parameter_description, requirement_type, verification_method, penalty_points)
VALUES 
    (1, 1, 'Minimum 6-meter clear fire tender access driveway surrounding structure', 'MANDATORY', 'PHYSICAL_MEASUREMENT', 10),
    (1, 2, 'Wet riser system with operational landing valve on each floor', 'MANDATORY', 'PRESSURE_TEST', 10),
    (1, 3, 'Automatic sprinkler coverage across all basement parking levels', 'MANDATORY', 'PHYSICAL_INSPECTION', 8),
    (1, 4, 'Illuminated photoluminescent exit signage and emergency escape routes', 'MANDATORY', 'VISUAL_INSPECTION', 5)
ON CONFLICT DO NOTHING;

-- 6. Standard Training Courses (From Verified Legacy Table 47)
INSERT INTO firesphere.training_courses (org_id, course_code, title, duration_hours, description)
VALUES 
    (1, 'TRG-NBC-101', 'NBC 2016 Part 4 Inspection Standards & Legal Procedures', 40, 'Statutory training for field inspectors on code compliance enforcement'),
    (1, 'TRG-HAZ-202', 'Hazardous Materials & Chemical Fire Suppression', 60, 'Specialized training on industrial hazmat mitigation and foam apparatus deployment')
ON CONFLICT DO NOTHING;
