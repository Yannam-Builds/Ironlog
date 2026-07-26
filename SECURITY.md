# Security policy

## Reporting a vulnerability

Do not open a public issue for a vulnerability that could expose fitness data, progress photos, backup contents, API keys, signing material, or another user's device data.

Email **ironlogsupport@gmail.com** with:

- the affected version or commit;
- a concise reproduction;
- the likely impact;
- any logs or proof of concept with personal data removed.

You should receive an acknowledgement within seven days. Please allow time for validation and a coordinated fix before publishing details.

## Supported code

Security fixes target the current default branch and the latest published build when one exists. Historical branches and untagged artifacts are not supported releases.

## Sensitive files

Release keystores, `local.properties`, provider API keys, user exports, databases, progress photos, and device logs must never be committed. Contributors must use their own signing material for local release builds.
