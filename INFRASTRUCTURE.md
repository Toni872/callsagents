# Callsagents — Infrastructure & Control Links

## Railway (Infrastructure)

| Service | Dashboard | Logs |
|---------|-----------|------|
| Callsagents Backend | [renewed-reverence/backend](https://railway.com/project/5d126558-22c4-4955-b7b4-a57e6a61baf9/service/1adfc32b-c4b6-4760-967f-82a5daa393d2) | `railway logs --service callsagents-backend` |
| Callsagents Frontend | [renewed-reverence/frontend](https://railway.com/project/5d126558-22c4-4955-b7b4-a57e6a61baf9/service/cbdd26bf-3f3b-43b1-9f28-d098ccabf828) | `railway logs --service callsagents-frontend` |
| Script9 | [beautiful-enthusiasm/script9](https://railway.com/project/50c50219-f525-4828-92bb-f4ff930db508/service/67aa01d4-7cf9-4196-893d-1a9d406da9a5) | `railway logs --service script9` |

## Production URLs

| URL | What |
|-----|------|
| https://callsagents-frontend-production.up.railway.app | SaaS dashboard (login, register, app) |
| https://callsagents-production.up.railway.app/api/health | Backend health check |
| https://www.script-9.com | Brand website |

## External APIs

| Service | Dashboard |
|---------|-----------|
| Supabase | https://supabase.com/dashboard/project/hxpsmwhlruklfrlstxxn |
| Stripe | https://dashboard.stripe.com |
| Retell AI | https://retellai.com/dashboard |
| Groq | https://console.groq.com |
| Vonage | https://dashboard.vonage.com |
| Google Cloud Console | https://console.cloud.google.com |
| Resend | https://resend.com/emails |
| Slack webhooks | Configured in channel |

## GitHub

| Repo | URL |
|------|-----|
| Callsagents | https://github.com/Toni872/callsagents |
| Script9 | https://github.com/Toni872/script9 |

## Quick Health Check

```bash
railway status
curl -s https://callsagents-production.up.railway.app/api/health
curl -s -o /dev/null -w "%{http_code}" https://www.script-9.com
curl -s -o /dev/null -w "%{http_code}" https://callsagents-frontend-production.up.railway.app
```

## Admin Credentials

| Item | Value |
|------|-------|
| Admin email | contact@script-9.com |
| Admin password | Calls@gents2025! |

## Railway Account

| Item | Value |
|------|-------|
| Account | legionlord90@gmail.com |
| Workspace | Toni Lloret's Projects |
| Plan | Hobby per service |
