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
2. Send it to project maintainers through a private communication channel.

If no private channel is available yet, create one before public release and update this file.

## Response Targets (Best Effort)

- Initial acknowledgment: within 5 business days
- Triage and impact assessment: within 10 business days
- Fix timeline: depends on severity and complexity

## Security Best Practices for Deployments

- Always provide strong values for `JWT_SECRET` and `ADMIN_SECRET`
- Avoid default credentials in production
- Restrict database/network access
- Enable HTTPS at ingress/load balancer layer
- Keep dependencies and base images updated
