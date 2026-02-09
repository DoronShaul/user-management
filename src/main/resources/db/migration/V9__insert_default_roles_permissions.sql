-- Insert default roles and permissions seed data

-- Insert default roles
INSERT INTO roles (name, description, created_at) VALUES
('ROLE_ADMIN', 'Administrator with full system access', NOW()),
('ROLE_USER', 'Standard user with basic access', NOW()),
('ROLE_MODERATOR', 'Moderator with elevated privileges', NOW());

-- Insert default permissions
INSERT INTO permissions (name, description, resource, action, created_at) VALUES
('USER_READ', 'Read user information', 'USER', 'READ', NOW()),
('USER_CREATE', 'Create new users', 'USER', 'CREATE', NOW()),
('USER_UPDATE', 'Update user information', 'USER', 'UPDATE', NOW()),
('USER_DELETE', 'Delete users', 'USER', 'DELETE', NOW()),
('ROLE_READ', 'Read role information', 'ROLE', 'READ', NOW()),
('ROLE_MANAGE', 'Manage roles and permissions', 'ROLE', 'MANAGE', NOW());

-- Assign all permissions to ROLE_ADMIN
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p WHERE r.name = 'ROLE_ADMIN';

-- Assign read permissions to ROLE_USER
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ROLE_USER' AND p.name IN ('USER_READ', 'ROLE_READ');

-- Assign read and update permissions to ROLE_MODERATOR
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ROLE_MODERATOR' AND p.name IN ('USER_READ', 'USER_UPDATE', 'ROLE_READ');
