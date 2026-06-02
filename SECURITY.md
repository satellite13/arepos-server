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

## Response Targets (Best Effort)

- Initial acknowledgment: within 5 business days
- Triage and impact assessment: within 10 business days
- Fix timeline: depends on severity and complexity

## Security Best Practices for Deployments

- Always provide a strong `JWT_SECRET` (minimum 32 bytes); startup fails when blank/weak
- Configure explicit `WEBSOCKET_ALLOWED_ORIGIN_PATTERNS` in `prod` (wildcard `*` is rejected)
- Keep `ADMIN_SECRET` disabled unless admin bootstrap is intentionally required
- Ensure MinIO credentials are non-default in `prod`
- Monitor `/actuator/health` and `/actuator/prometheus` (especially authz/outbox contributors)
- Keep Cerbos reachable and policy bundle up to date
- Avoid default credentials in production
- Restrict database/network access
- Enable HTTPS at ingress/load balancer layer
- Keep dependencies and base images updated
