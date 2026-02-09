# Plan Agent Lessons

Patterns and preferences learned from previous iterations.

## Decision Making

- Always ask before assuming a dependency shouldn't be added
- Prefer Spring ecosystem solutions (actuator, validation, etc.) over custom implementations
- When in doubt, present options rather than picking one

## Health Checks

- Health endpoints should actually verify dependencies (DB, external services)
- Consider Spring Actuator before building custom health endpoints
- If custom health is needed, check what the app depends on and verify those

## General Preferences

- User prefers to be consulted on architectural decisions
- Simpler is better, but not at the cost of functionality
