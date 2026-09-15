# Security Policy

## Supported version

Security fixes are applied to the current `main` branch.

## Reporting a vulnerability

Please do not publish exploitable security details in a public issue before a fix is available.

When reporting a problem, include:

- affected component and version/commit;
- minimal steps to reproduce;
- expected and actual behavior;
- security impact;
- suggested mitigation, if known.

## Sensitive data

Marc2BF processes bibliographic records. Do not include credentials, private patron information, restricted catalog data, API secrets, or other confidential material in bug reports, examples, commits, or test fixtures.

## Scope

Security-relevant issues may include XML parser behavior, path handling, unsafe external resource resolution, dependency vulnerabilities, malformed MARC input handling, or unexpected file overwrite behavior.
