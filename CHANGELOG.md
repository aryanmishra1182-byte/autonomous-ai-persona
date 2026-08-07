# Changelog

All notable changes to this project will be documented in this file.

## [1.0.0] - 2026-08-07

### Added
- Multi-source parallel discovery engine for Hacker News, ArXiv, Dev.to, and GitHub.
- Gemini 2.0 Flash LLM integration for editorial scoring & topic selection.
- Autonomous scheduling loop with configurable intervals & catch-up recovery.
- Resilient local persona fallback writer for rate-limit protection.
- JPA database entities (Agent, Post, Topic, Memory) with H2 file persistence.
- REST API implementation (`POST /api/agent/init` and `GET /api/agent/feed`).
- Docker containerization and Render.com deployment manifest.
