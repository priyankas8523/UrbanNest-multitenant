package com.keycloak.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
/**
 * Entry point for the Multitenant Application.
 *
 * This is a Spring Boot application that implements a schema-per-tenant
 * multitenancy architecture with Keycloak for identity management.
 *
 * Key architectural decisions:
 * - Each company (tenant) gets its own PostgreSQL schema for data isolation
 * - Keycloak handles all authentication, user management, and role assignment
 * - Two portals exist: Company Portal (admin/employees) and Client Portal (end users)
 *
 * @EnableJpaAuditing - Turns on automatic population of created_at, updated_at,
 *                      created_by, updated_by fields on all entities that extend AuditEntity.
 *                      The "who" part (created_by) is resolved by AuditorAwareImpl using the JWT subject.
 */
@SpringBootApplication
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

}
