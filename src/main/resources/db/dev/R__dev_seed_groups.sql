-- Dev-only repeatable seed data for quick matching tests.
-- Loaded via %dev Flyway location: classpath:db/dev
-- Source: ESCO skills + job tags derived communities/events

INSERT INTO mesh.mesh_node (id, created_by, node_type, title, description, searchable, created_at, updated_at)
VALUES ('d0000000-0000-0000-0000-000000000013', 'd0000000-0000-0000-0000-000000000013', 'USER', 'System: dev-seed-groups', 'System owner for synthetic group seeds.', false, now(), now())
ON CONFLICT (id) DO UPDATE SET searchable = false, updated_at = now();

INSERT INTO identity.user_identity (id, oauth_provider, oauth_subject, node_id, can_create_job, can_manage_skills)
VALUES ('d1000000-0000-0000-0000-000000000013', 'dev-seed', 'groups-owner', 'd0000000-0000-0000-0000-000000000013', false, false)
ON CONFLICT (id) DO NOTHING;

DELETE FROM mesh.mesh_node
WHERE created_by = 'd0000000-0000-0000-0000-000000000013' AND node_type IN ('COMMUNITY', 'EVENT', 'INTEREST_GROUP');

INSERT INTO mesh.mesh_node (id, created_by, node_type, title, description, tags, structured_data, country, searchable, external_id, embedding, created_at, updated_at)
VALUES
('b85c515b-f8eb-563a-ade3-ba71f780b967', 'd0000000-0000-0000-0000-000000000013', 'COMMUNITY', 'Graphic Arts And Communication Design Guild #001', 'Public-source inspired community focused on graphic arts and communication design.', ARRAY['graphic arts and communication design','it','community'], '{"topic": "graphic arts and communication design", "member_count": 69, "source": "arbeitnow-tags + esco-skills"}'::jsonb, 'DE', true, NULL, NULL, now(), now()),
('9c6df616-845f-5d27-b591-7d5e6663d729', 'd0000000-0000-0000-0000-000000000013', 'COMMUNITY', 'Data Scientist Guild #002', 'Public-source inspired community focused on data scientist.', ARRAY['data scientist','it','community'], '{"topic": "data scientist", "member_count": 98, "source": "arbeitnow-tags + esco-skills"}'::jsonb, 'IT', true, NULL, NULL, now(), now()),
('f9358fa6-d18a-58b2-aafe-b3f46f447be3', 'd0000000-0000-0000-0000-000000000013', 'EVENT', 'Product Management Summit #003', 'Public-source inspired event focused on product management.', ARRAY['product management','it','community'], '{"topic": "product management", "member_count": 127, "source": "arbeitnow-tags + esco-skills"}'::jsonb, 'FR', true, NULL, NULL, now(), now()),
('bd4eb21a-0ad9-5f73-802c-007dc49f2fc3', 'd0000000-0000-0000-0000-000000000013', 'COMMUNITY', 'Industrial Software Guild #004', 'Public-source inspired community focused on industrial software.', ARRAY['industrial software','it','community'], '{"topic": "industrial software", "member_count": 156, "source": "arbeitnow-tags + esco-skills"}'::jsonb, 'IN', true, NULL, NULL, now(), now());
