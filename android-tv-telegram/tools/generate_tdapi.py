"""Generate org.drinkless.tdlib.TdApi from TDLib's authoritative td_api.tl.

Mirrors the naming and type rules in TDLib's own td/generate/tl_writer_java.cpp
closely enough for javac and kotlinc to type-check real client code against it.

WHAT THIS IS NOT: the CONSTRUCTOR values are sequential integers, not the real
CRC32 hashes of the TL signatures, and the classes carry no serialization. The
output is a compile-time verification harness only. It is written to the build
directory, never to app/libs, and never ships in an APK.

Usage: generate_tdapi.py <path to td_api.tl> <output TdApi.java>
"""
import re
import sys

BUILTIN = {
    "int32": "int", "int53": "long", "int64": "long",
    "double": "double", "string": "String", "bytes": "byte[]", "Bool": "boolean",
}
BOXED = {"int": "Integer", "long": "Long", "double": "Double", "boolean": "Boolean"}
SKIP_DECLS = {"double", "string", "int32", "int53", "int64", "bytes", "boolFalse", "boolTrue", "vector"}


def class_name(name):
    out, upper = [], True
    for ch in name:
        if not ch.isalnum():
            upper = True
            continue
        out.append(ch.upper() if upper else ch)
        upper = False
    return "".join(out)


def field_name(name):
    out, upper = [], False
    for ch in name:
        if not ch.isalnum():
            upper = True
            continue
        out.append(ch.upper() if upper else ch)
        upper = False
    return "".join(out)


def java_type(tl):
    tl = tl.strip()
    m = re.fullmatch(r"[Vv]ector<(.+)>", tl)
    if m:
        return java_type(m.group(1)) + "[]"
    if tl in BUILTIN:
        return BUILTIN[tl]
    return class_name(tl)


def parse(path):
    types, functions, order = {}, [], []
    in_functions = False
    buf = ""
    for raw in open(path, encoding="utf-8"):
        line = raw.rstrip("\n")
        if line.strip() == "---functions---":
            in_functions = True
            continue
        if line.strip() == "---types---":
            in_functions = False
            continue
        if line.lstrip().startswith("//") or not line.strip():
            continue
        buf += " " + line.strip()
        if ";" not in buf:
            continue
        decl, buf = buf.strip().rstrip(";").strip(), ""
        if "=" not in decl:
            continue
        left, result = decl.rsplit("=", 1)
        tokens = left.split()
        if not tokens or tokens[0] in SKIP_DECLS or "{" in left or "?" in left:
            continue
        name, fields = tokens[0], []
        for token in tokens[1:]:
            if ":" not in token:
                continue
            fname, ftype = token.split(":", 1)
            fields.append((field_name(fname), java_type(ftype)))
        result = result.strip()
        if in_functions:
            functions.append((name, fields, result))
        else:
            if result not in types:
                types[result] = []
                order.append(result)
            types[result].append((name, fields))
    return types, functions, order


def emit_class(out, java_name, base, fields, ctor_id, abstract=False):
    header = "abstract static class" if abstract else "static class"
    out.append("    public %s %s extends %s {" % (header, java_name, base))
    for fname, ftype in fields:
        out.append("        public %s %s;" % (ftype, fname))
    out.append("        public %s() {" % java_name)
    out.append("        }")
    if fields:
        args = ", ".join("%s %s" % (t, n) for n, t in fields)
        out.append("        public %s(%s) {" % (java_name, args))
        for fname, _ in fields:
            out.append("            this.%s = %s;" % (fname, fname))
        out.append("        }")
    if not abstract:
        out.append("        public static final int CONSTRUCTOR = %d;" % ctor_id)
        out.append("        @Override")
        out.append("        public int getConstructor() {")
        out.append("            return CONSTRUCTOR;")
        out.append("        }")
    out.append("    }")
    out.append("")


def main(scheme, target):
    types, functions, order = parse(scheme)
    out = [
        "package org.drinkless.tdlib;",
        "",
        "/** Generated from td_api.tl for compile-time verification only. */",
        "public class TdApi {",
        "    public abstract static class Object {",
        "        public Object() {",
        "        }",
        "        public abstract int getConstructor();",
        "    }",
        "",
        "    public abstract static class Function<R extends Object> extends Object {",
        "        public Function() {",
        "        }",
        "    }",
        "",
    ]
    next_id = 1
    emitted = set()
    for type_name in order:
        ctors = types[type_name]
        java_type_name = class_name(type_name)
        single = len(ctors) == 1 and class_name(ctors[0][0]) == java_type_name
        if not single:
            emit_class(out, java_type_name, "Object", [], 0, abstract=True)
            emitted.add(java_type_name)
        for ctor, fields in ctors:
            cname = class_name(ctor)
            base = "Object" if single else java_type_name
            emit_class(out, cname, base, fields, next_id)
            emitted.add(cname)
            next_id += 1
    for name, fields, result in functions:
        ret = java_type(result)
        ret = BOXED.get(ret, ret)
        emit_class(out, class_name(name), "Function<%s>" % ret, fields, next_id)
        next_id += 1
    out.append("}")
    open(target, "w", encoding="utf-8").write("\n".join(out) + "\n")
    print("types: %d, functions: %d, classes: %d" % (len(types), len(functions), next_id - 1))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
