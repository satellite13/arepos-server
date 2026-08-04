# Security Policy

## Supported Versions

Security fixes are currently provided on a best-effort basis for the latest `master` state.

## Reporting a Vulnerability

Please do **not** open public issues for security vulnerabilities.

Instead:

1. Prepare a private report including:
   - vulnerability description
   - impact
   - reproduction steps
   - possible mitigation
2. Send it to project maintainers through a private communication channel:
   - private vulnerability/advisory report in the Git hosting platform (preferred), or
   - direct private message/email to maintainers.

If no private channel is available yet, create one before public release and update this file.

## Security Best Practices for Deployments

- Always provide a strong `JWT_SECRET` (minimum 32 bytes); startup fails when blank/weak
- Configure explicit `WEBSOCKET_ALLOWED_ORIGIN_PATTERNS` in `prod` (wildcard `*` is rejected)
- Set `AREPOS_AUTH_COOKIE_SECURE=true` behind HTTPS (Secure flag on auth cookies)
- Keep `AREPOS_AUTH_CSRF_ENABLED=true` for browser cookie sessions

### CORS and WebSocket origins (same-origin default)

Typical wArchi enterprise deployment is **same-origin**: the browser talks only to warchi (nginx), which proxies `/api/` and `/ws` to arepos-server. In this setup **REST CORS is not required** — the browser sees a single origin.

- **REST CORS:** enable only for split-domain setups (SPA and API on different origins). Use an explicit origin whitelist with `allowCredentials: true`; never use `*` with cookies.
- **WebSocket origins:** always set `WEBSOCKET_ALLOWED_ORIGIN_PATTERNS` in production to match the public UI origin(s), e.g. `https://warchi.example.com`. Wildcard `*` is rejected in `prod`.
- **Runbook:** if live sync fails after hardening, check browser console for CSP/connect errors and server logs for WebSocket origin rejection before enabling REST CORS.
- Keep `ADMIN_SECRET` disabled unless admin bootstrap is intentionally required
- Ensure MinIO credentials are non-default in `prod`
- Monitor `/actuator/health` and `/actuator/prometheus` (especially authz/outbox contributors)
- Keep Cerbos reachable and policy bundle up to date
- Avoid default credentials in production
- Restrict database/network access
- Enable HTTPS at ingress/load balancer layer
- Keep dependencies and base images updated
