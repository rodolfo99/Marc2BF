# Contributing to Marc2BF

Thanks for your interest in improving Marc2BF.

## Good contributions

Contributions are especially welcome for:

- MARC21 and BIBFRAME mapping tests.
- Compatibility fixes for ISO2709 and MARCXML.
- RDF serialization and validation improvements.
- Documentation and reproducible examples.
- Performance improvements for large catalogs.
- Docker and CI improvements.

## Before opening a pull request

1. Fork the repository and create a focused branch.
2. Keep changes small and easy to review.
3. Add or update tests when behavior changes.
4. Run:

```bash
mvn clean verify
```

5. For changes that can affect conversion parity, also run:

```bash
python3 -m venv .venv
.venv/bin/pip install -r scripts/parity-requirements.txt
.venv/bin/python scripts/verify-parity.py
```

## Pull requests

Please describe:

- the problem being solved;
- the relevant MARC21/BIBFRAME case;
- how the change was tested;
- whether generated RDF changes;
- any compatibility implications.

## Reporting bugs

Include a minimal reproducible input whenever licensing and privacy permit. Never upload private library records or patron data.

## Style

- Java 21.
- Prefer clear code over clever code.
- Keep public behavior documented.
- Preserve existing compatibility unless a breaking change is clearly justified.

## Third-party material

Do not submit copyrighted datasets, credentials, private records, or third-party code without compatible licensing.
