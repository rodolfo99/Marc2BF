#!/usr/bin/env python3
"""Paridad: motor LC original en disco (s9api) frente al JAR (JAXP), ambos con Saxon.

No descarga reglas, no altera fixtures ni elimina propiedades para hacer coincidir grafos.
Requiere mvn verify, Python, lxml y rdflib. Guarda resultados en target/parity/.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys

from lxml import etree
from rdflib import Graph
import rdflib
from rdflib.compare import isomorphic, graph_diff, to_isomorphic

rdflib.NORMALIZE_LITERALS = False

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--java", default="java")
parser.add_argument("--limit", type=int, default=0, help="0: todos los fixtures")
parser.add_argument("--reference", choices=("saxon", "libxslt"), default="saxon",
                    help="libxslt: diagnóstico adicional de diferencias entre procesadores")
args = parser.parse_args()
out = ROOT / "target/parity"
out.mkdir(parents=True, exist_ok=True)
fixtures = sorted((ROOT / "vendor/lc/test/data").rglob("*.xml"))
if args.limit:
    fixtures = fixtures[:args.limit]
tasks = []
for index, fixture in enumerate(fixtures):
    for split in (False, True):
        actual = out / f"{index:03d}-{'split' if split else 'direct'}.rdf"
        tasks.append((fixture, actual, split))
taskfile = out / "tasks.tsv"
taskfile.write_text("".join(f"{source}\t{target}\t{str(split).lower()}\n" for source, target, split in tasks))
classpath = os.pathsep.join(str(ROOT / path) for path in ("target/test-classes", "target/marc2bf.jar"))
with (out / "java.log").open("w") as log:
    subprocess.run([args.java, "-Xmx2g", "-cp", classpath, "mx.ucol.marc2bf.ParityHarness", str(taskfile)],
                   cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, check=True)
if args.reference == "saxon":
    with (out / "reference.log").open("w") as log:
        subprocess.run([args.java, "-Xmx2g", "-cp", classpath, "mx.ucol.marc2bf.StandaloneReference", str(taskfile)],
                       cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, check=True)
xml_parser = etree.XMLParser(resolve_entities=False, no_network=True)
access = etree.XSLTAccessControl(read_file=True, read_network=False, write_file=False, write_network=False)
styles = ROOT / "vendor/lc/xsl"
preprocess = etree.XSLT(etree.parse(str(styles / "ConvSpec-Preprocess0-Splitting.xsl"), xml_parser), access_control=access)
convert = etree.XSLT(etree.parse(str(styles / "marc2bibframe2.xsl"), xml_parser), access_control=access)
parameters = {"baseuri": etree.XSLT.strparam("https://example.org/catalog/"),
              "pGenerationDatestamp": etree.XSLT.strparam("2026-09-09T00:00:00Z"),
              "localfields": "false()", "bcp47inferrence": "true()", "serialization": etree.XSLT.strparam("rdfxml")}
results = []
for fixture, actual, split in tasks:
    try:
        if args.reference == "saxon":
            expected_xml = Path(str(actual) + ".reference.rdf").read_bytes()
        else:
            source = etree.parse(str(fixture), xml_parser)
            if split:
                source = preprocess(source, **parameters)
            expected_xml = bytes(convert(source, **parameters))
    except etree.XSLTApplyError as error:
        results.append({"fixture": fixture.relative_to(ROOT).as_posix(), "preprocess": split,
                        "isomorphic": False, "reference_error": str(error)})
        print("REFERENCE ERROR", fixture, split, error, flush=True)
        continue
    expected = Graph().parse(data=expected_xml, format="xml")
    observed = Graph().parse(actual, format="xml")
    equal = isomorphic(expected, observed)
    result = {"fixture": fixture.relative_to(ROOT).as_posix(), "preprocess": split,
              "expected_triples": len(expected), "actual_triples": len(observed), "isomorphic": equal}
    results.append(result)
    if not equal:
        _, only_expected, only_actual = graph_diff(to_isomorphic(expected), to_isomorphic(observed))
        only_expected.serialize(out / (actual.stem + "-expected-only.ttl"), format="turtle")
        only_actual.serialize(out / (actual.stem + "-actual-only.ttl"), format="turtle")
    print(("PASS" if equal else "FAIL"), fixture.relative_to(ROOT), "split=" + str(split), flush=True)
report = {"lc_commit": "ed9abb038214474e8fc8ba4035d01c42fe0246de",
          "fixtures": len(fixtures), "cases": len(results), "passed": sum(r["isomorphic"] for r in results),
          "reference": args.reference, "candidate": "Saxon-HE 12.5 / LcEngine", "results": results}
report_path = out / ("report.json" if args.reference == "saxon" else "libxslt-diagnostic.json")
report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
print(f"{report['passed']}/{len(results)} grafos isomorfos. Reporte: {report_path}")
sys.exit(0 if report["passed"] == len(results) else 1)
