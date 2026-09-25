"""Check the shipped English/Hindi Android catalog without Android tooling."""
from pathlib import Path
from collections import Counter
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1] / "app/src/main/res"
FORMAT = re.compile(r"%(?:\d+\$)?[sdif]")


def catalog(folder):
    result = {}
    for path in sorted(folder.glob("*.xml")):
        for node in ET.parse(path).getroot():
            if node.tag not in ("string", "plurals") or node.get("translatable") == "false":
                continue
            key = node.get("name")
            assert key not in result, f"Duplicate resource: {folder.name}/{key}"
            if node.tag == "string":
                result[key] = {"string": "".join(node.itertext())}
            else:
                result[key] = {item.get("quantity"): "".join(item.itertext()) for item in node}
    return result


def main():
    english, hindi = catalog(ROOT / "values"), catalog(ROOT / "values-hi")
    assert english.keys() == hindi.keys(), f"Missing/extra Hindi keys: {english.keys() ^ hindi.keys()}"
    for key, forms in english.items():
        assert forms.keys() == hindi[key].keys(), f"Plural forms differ: {key}"
        for form, text in forms.items():
            translated = hindi[key][form]
            assert translated.strip('" '), f"Empty Hindi text: {key}"
            assert Counter(FORMAT.findall(text)) == Counter(FORMAT.findall(translated)), f"Format arguments differ: {key}/{form}"
            assert "\ufffd" not in text + translated, f"Invalid Unicode: {key}"
    print(f"PASS: {len(english)} resources; English/Hindi key, plural, placeholder and Unicode parity")


if __name__ == "__main__":
    main()
