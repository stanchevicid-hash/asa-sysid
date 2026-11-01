# Regenerates src/org/asa/tools/ftc/AsaWebPage.java from web/index.html (run after editing the page).
import json, pathlib
root = pathlib.Path(__file__).parent
html = (root / "web" / "index.html").read_text(encoding="utf-8")
lines = ",\n".join("        " + json.dumps(l + "\n", ensure_ascii=True) for l in html.splitlines())
(root / "src/org/asa/tools/ftc/AsaWebPage.java").write_text(
    "package org.asa.tools.ftc;\n\n"
    "// GENERATED from web/index.html by gen-page.py - edit the HTML, then run: python gen-page.py\n"
    "final class AsaWebPage {\n    private AsaWebPage() {}\n"
    "    private static final String[] LINES = {\n" + lines + "\n    };\n"
    "    static String html() {\n        StringBuilder s = new StringBuilder();\n"
    "        for (String l : LINES) s.append(l);\n        return s.toString();\n    }\n}\n",
    encoding="utf-8")
print("ok")
