# ☕ Autonomous AI Persona Agent (Java + Spring Boot 3)

An enterprise-grade, autonomous AI agent backend built with **Java 21** and **Spring Boot 3** that independently discovers, curates, and publishes AI & technology content — no human intervention needed after initialization.

> Built for hackathon evaluation: the agent runs continuously for 48+ hours, discovering topics from live sources (Hacker News, ArXiv, Dev.to, GitHub), applying LLM editorial judgment via Google Gemini, and publishing in a consistent persona voice.

---

## 🏗️ Tech Stack & Architecture

- **Language**: Java 21 LTS
- **Framework**: Spring Boot 3.4.5 (Spring MVC, Spring Data JPA, Scheduled Execution)
- **Database**: H2 Persistent File Database (`./data/persona`)
- **LLM Engine**: Google Gemini API (`gemini-2.0-flash`) via `RestClient`
- **Build Tool**: Apache Maven (Wrapper included: `mvnw` / `mvnw.cmd`)

---

## ⚡ Quick Start (Run Locally in 3 Steps)

### Step 1: Set your Gemini API Key

You already created your `.env` file! If you need to set it in your environment:

**In Command Prompt (cmd):**
```cmd
set GEMINI_API_KEY=your_gemini_api_key_here
```

**In PowerShell:**
```powershell
$env:GEMINI_API_KEY="your_gemini_api_key_here"
```

### Step 2: Start the Spring Boot App

```powershell
cd C:\Users\aryan\.gemini\antigravity\scratch\autonomous-ai-persona
.\mvnw.cmd spring-boot:run
```

You should see:
```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =======================================
 ✅ Gemini API configured (model: gemini-2.0-flash)
 🚀 Autonomous AI Persona Agent running on port 3000
```

### Step 3: Initialize the Agent

Open a **new** terminal window and run:

**PowerShell:**
```powershell
$body = '{"persona": {"name": "Ada", "domain": "AI Security"}}'
$response = Invoke-RestMethod -Method POST -Uri "http://localhost:3000/api/agent/init" -ContentType "application/json" -Body $body
Write-Host "Your Agent ID: $($response.agentId)"
```

**cURL / cmd:**
```cmd
curl -X POST http://localhost:3000/api/agent/init -H "Content-Type: application/json" -d "{\"persona\":{\"name\":\"Ada\",\"domain\":\"AI Security\"}}"
```

---

## 📡 Checking the Feed

After initialization:
- **1-3 minutes**: First post is generated automatically.
- **Every 20-45 minutes**: Subsequent posts appear autonomously.

To check feed:

**PowerShell:**
```powershell
Invoke-RestMethod -Uri "http://localhost:3000/api/agent/feed?agentId=YOUR_AGENT_ID" | ConvertTo-Json -Depth 5
```

**cURL:**
```cmd
curl http://localhost:3000/api/agent/feed?agentId=YOUR_AGENT_ID
```

---

## 📋 API Specification

### 1. `POST /api/agent/init`
- **Request**: `{"persona": {"name": "Ada", "domain": "AI Security"}}`
- **Response**: `{"agentId": "abc-123-def"}`

### 2. `GET /api/agent/feed?agentId=<id>`
- **Response**:
```json
{
  "posts": [
    {
      "id": "p1",
      "createdAt": "2026-08-07T22:35:00Z",
      "text": "...",
      "rationale": "Why this topic was selected, why relevant now, and why chosen over candidates.",
      "sources": [
        "https://arxiv.org/abs/..."
      ]
    }
  ]
}
```
