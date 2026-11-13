#!/usr/bin/env python3
"""Small class-file edits used by the Java 1.2-compatible patch builds."""

import struct
import sys
import zipfile

CONTROLLER = (
    "de/esolutions/hmi/widgets/audi/evo/high/widgets/"
    "CombiMapController"
)
CONNECTED_DESC = (
    "(Lde/esolutions/hmi/widgets/audi/base/InitializationContext;)V"
)
UPDATE_DESC = "(Lde/audi/atip/hmi/event/ModelUpdateEvent;)V"
BRIDGE_CLASS = "local/clustermap/ViewMode"
BRIDGE_DESC = "(Ljava/lang/Object;Ljava/lang/Object;)V"
INJECT_SIZE = 8


class Reader:
    def __init__(self, data):
        self.data = data
        self.pos = 0

    def take(self, length):
        end = self.pos + length
        if length < 0 or end > len(self.data):
            raise ValueError("truncated class file")
        value = self.data[self.pos:end]
        self.pos = end
        return value

    def u1(self):
        return self.take(1)[0]

    def u2(self):
        return struct.unpack(">H", self.take(2))[0]

    def u4(self):
        return struct.unpack(">I", self.take(4))[0]


def u2(value):
    return struct.pack(">H", value)


def u4(value):
    return struct.pack(">I", value)


def modified_utf8(data):
    return data.replace(b"\xc0\x80", b"\0").decode("utf-8", "surrogatepass")


def parse_pool(reader):
    count = reader.u2()
    start = reader.pos
    pool = [None] * count
    index = 1
    while index < count:
        tag = reader.u1()
        if tag == 1:
            length = reader.u2()
            raw = reader.take(length)
            pool[index] = (tag, modified_utf8(raw))
        elif tag in (3, 4):
            raw = reader.take(4)
            value = struct.unpack(">i", raw)[0] if tag == 3 else None
            pool[index] = (tag, value)
        elif tag in (5, 6):
            pool[index] = (tag, reader.take(8))
            index += 1
        elif tag in (7, 8, 16, 19, 20):
            pool[index] = (tag, reader.u2())
        elif tag in (9, 10, 11, 12, 17, 18):
            pool[index] = (tag, (reader.u2(), reader.u2()))
        elif tag == 15:
            pool[index] = (tag, (reader.u1(), reader.u2()))
        else:
            raise ValueError("unsupported constant-pool tag %d" % tag)
        index += 1
    return count, pool, reader.data[start:reader.pos]


def pool_utf8(pool, index):
    entry = pool[index]
    if entry is None or entry[0] != 1:
        raise ValueError("expected UTF-8 constant")
    return entry[1]


def class_name(pool, index):
    entry = pool[index]
    if entry is None or entry[0] != 7:
        raise ValueError("expected class constant")
    return pool_utf8(pool, entry[1])


def copy_attributes(reader, output, pool, transform_code=None):
    count = reader.u2()
    output.extend(u2(count))
    for _ in range(count):
        name_index = reader.u2()
        length = reader.u4()
        info = reader.take(length)
        name = pool_utf8(pool, name_index)
        if name == "Code" and transform_code is not None:
            info = transform_code(info, pool)
        output.extend(u2(name_index))
        output.extend(u4(len(info)))
        output.extend(info)


def copy_member(reader, output, pool):
    start = reader.pos
    reader.take(6)
    copy_attributes(reader, bytearray(), pool)
    output.extend(reader.data[start:reader.pos])


def adjust_line_numbers(info):
    reader = Reader(info)
    count = reader.u2()
    output = bytearray(u2(count))
    for _ in range(count):
        start = reader.u2() + INJECT_SIZE
        line = reader.u2()
        if start > 0xffff:
            raise ValueError("line-number offset overflow")
        output.extend(u2(start))
        output.extend(u2(line))
    if reader.pos != len(info):
        raise ValueError("malformed LineNumberTable")
    return bytes(output)


def adjust_local_variables(info):
    reader = Reader(info)
    count = reader.u2()
    output = bytearray(u2(count))
    for _ in range(count):
        start = reader.u2() + INJECT_SIZE
        length = reader.u2()
        name = reader.u2()
        descriptor = reader.u2()
        slot = reader.u2()
        if start > 0xffff:
            raise ValueError("local-variable offset overflow")
        output.extend(u2(start))
        output.extend(u2(length))
        output.extend(u2(name))
        output.extend(u2(descriptor))
        output.extend(u2(slot))
    if reader.pos != len(info):
        raise ValueError("malformed LocalVariableTable")
    return bytes(output)


def transform_code(info, pool, method_kind=None, method_ref=None,
                   strip_frames=False):
    reader = Reader(info)
    max_stack = reader.u2()
    max_locals = reader.u2()
    code = reader.take(reader.u4())
    exception_count = reader.u2()
    exceptions = []
    for _ in range(exception_count):
        entry = [reader.u2(), reader.u2(), reader.u2(), reader.u2()]
        exceptions.append(entry)

    attr_count = reader.u2()
    attributes = []
    for _ in range(attr_count):
        name_index = reader.u2()
        name = pool_utf8(pool, name_index)
        attr = reader.take(reader.u4())
        if name == "StackMapTable":
            if not strip_frames:
                raise ValueError("target HMI unexpectedly has StackMapTable")
            continue
        if method_kind is not None:
            if name == "LineNumberTable":
                attr = adjust_line_numbers(attr)
            elif name in ("LocalVariableTable", "LocalVariableTypeTable"):
                attr = adjust_local_variables(attr)
            else:
                raise ValueError("unsupported Code attribute %s" % name)
        attributes.append((name_index, attr))
    if reader.pos != len(info):
        raise ValueError("malformed Code attribute")

    if method_kind is not None:
        if len(code) + INJECT_SIZE > 0xffff:
            raise ValueError("target method code is too large")
        if method_kind == "connected":
            prefix = b"\x2a\x01\xb8" + u2(method_ref) + b"\x00\x00\x00"
        else:
            prefix = b"\x2a\x2b\xb8" + u2(method_ref) + b"\x00\x00\x00"
        code = prefix + code
        max_stack = max(max_stack, 2)
        exceptions = [
            [start + INJECT_SIZE, end + INJECT_SIZE,
             handler + INJECT_SIZE, catch]
            for start, end, handler, catch in exceptions
        ]
        if any(max(value) > 0xffff for value in exceptions):
            raise ValueError("target exception-table offset overflow")

    output = bytearray()
    output.extend(u2(max_stack))
    output.extend(u2(max_locals))
    output.extend(u4(len(code)))
    output.extend(code)
    output.extend(u2(len(exceptions)))
    for entry in exceptions:
        for value in entry:
            output.extend(u2(value))
    output.extend(u2(len(attributes)))
    for name_index, attr in attributes:
        output.extend(u2(name_index))
        output.extend(u4(len(attr)))
        output.extend(attr)
    return bytes(output)


def inject_view_mode(data):
    reader = Reader(data)
    magic = reader.u4()
    minor = reader.u2()
    major = reader.u2()
    if magic != 0xcafebabe or major != 46:
        raise ValueError("expected a Java 1.2-level P5089 class file")
    pool_count, pool, pool_bytes = parse_pool(reader)
    if not any(entry is not None and entry[0] == 3 and entry[1] == 402521
               for entry in pool):
        raise ValueError("NAV_VIEW_SIZE_CHOICE model id 402521 not found")

    class_index = pool_count
    method_name_index = pool_count + 2
    descriptor_index = pool_count + 3
    name_type_index = pool_count + 4
    method_ref_index = pool_count + 5
    extension = bytearray()

    def add_utf8(text):
        encoded = text.encode("ascii")
        extension.extend(b"\x01" + u2(len(encoded)) + encoded)

    add_utf8(BRIDGE_CLASS)
    extension.extend(b"\x07" + u2(class_index))
    add_utf8("sync")
    add_utf8(BRIDGE_DESC)
    extension.extend(b"\x0c" + u2(method_name_index) + u2(descriptor_index))
    extension.extend(b"\x0a" + u2(class_index + 1) + u2(name_type_index))

    body = Reader(data[reader.pos:])
    output = bytearray()
    output.extend(u4(magic))
    output.extend(u2(minor))
    output.extend(u2(major))
    output.extend(u2(pool_count + 6))
    output.extend(pool_bytes)
    output.extend(extension)
    output.extend(body.take(6))
    interface_count = body.u2()
    output.extend(u2(interface_count))
    output.extend(body.take(interface_count * 2))
    field_count = body.u2()
    output.extend(u2(field_count))
    for _ in range(field_count):
        copy_member(body, output, pool)

    method_count = body.u2()
    output.extend(u2(method_count))
    found = {"connected": 0, "update": 0}
    for _ in range(method_count):
        access = body.u2()
        name_index = body.u2()
        descriptor_index_member = body.u2()
        name = pool_utf8(pool, name_index)
        descriptor = pool_utf8(pool, descriptor_index_member)
        attrs_start = body.pos
        attributes_count = body.u2()
        output.extend(u2(access))
        output.extend(u2(name_index))
        output.extend(u2(descriptor_index_member))
        output.extend(u2(attributes_count))
        kind = None
        if name == "connected" and descriptor == CONNECTED_DESC:
            kind = "connected"
        elif name == "processModelUpdateEvent" and descriptor == UPDATE_DESC:
            kind = "update"
        code_count = 0
        for _ in range(attributes_count):
            attr_name_index = body.u2()
            attr_name = pool_utf8(pool, attr_name_index)
            attr = body.take(body.u4())
            if kind is not None and attr_name == "Code":
                attr = transform_code(
                    attr, pool, kind, method_ref_index, False)
                code_count += 1
            output.extend(u2(attr_name_index))
            output.extend(u4(len(attr)))
            output.extend(attr)
        if kind is not None:
            if code_count != 1:
                raise ValueError("target method has no unique Code attribute")
            found[kind] += 1
        if body.pos < attrs_start:
            raise ValueError("invalid method attributes")

    class_attribute_count = body.u2()
    output.extend(u2(class_attribute_count))
    for _ in range(class_attribute_count):
        name_index = body.u2()
        attr = body.take(body.u4())
        output.extend(u2(name_index))
        output.extend(u4(len(attr)))
        output.extend(attr)
    if body.pos != len(body.data):
        raise ValueError("unexpected trailing class-file data")
    if found != {"connected": 1, "update": 1}:
        raise ValueError("verified P5089 controller methods not found")
    if class_name(pool, struct.unpack(">H", data[reader.pos + 2:reader.pos + 4])[0]) != CONTROLLER:
        raise ValueError("unexpected controller class")
    return bytes(output)


def strip_frames(data):
    reader = Reader(data)
    magic = reader.u4()
    minor = reader.u2()
    reader.u2()
    if magic != 0xcafebabe:
        raise ValueError("not a class file")
    pool_count, pool, pool_bytes = parse_pool(reader)
    body = Reader(data[reader.pos:])
    output = bytearray()
    output.extend(u4(magic))
    output.extend(u2(minor))
    output.extend(u2(46))
    output.extend(u2(pool_count))
    output.extend(pool_bytes)
    output.extend(body.take(6))
    interface_count = body.u2()
    output.extend(u2(interface_count))
    output.extend(body.take(interface_count * 2))
    field_count = body.u2()
    output.extend(u2(field_count))
    for _ in range(field_count):
        copy_member(body, output, pool)

    method_count = body.u2()
    output.extend(u2(method_count))
    for _ in range(method_count):
        output.extend(body.take(6))
        copy_attributes(
            body, output, pool,
            lambda info, constants: transform_code(
                info, constants, strip_frames=True))

    class_attribute_count = body.u2()
    output.extend(u2(class_attribute_count))
    for _ in range(class_attribute_count):
        name_index = body.u2()
        attr = body.take(body.u4())
        output.extend(u2(name_index))
        output.extend(u4(len(attr)))
        output.extend(attr)
    if body.pos != len(body.data):
        raise ValueError("unexpected trailing class-file data")
    return bytes(output)


def main(argv):
    if len(argv) == 4 and argv[1] == "inject-view-insets":
        with zipfile.ZipFile(argv[2]) as stock:
            data = stock.read(CONTROLLER + ".class")
        with open(argv[3], "wb") as target:
            target.write(inject_view_mode(data))
        return
    if len(argv) == 3 and argv[1] == "strip-frames":
        with open(argv[2], "rb") as source:
            data = source.read()
        with open(argv[2], "wb") as target:
            target.write(strip_frames(data))
        return
    raise SystemExit(
        "usage: classfile_transform.py inject-view-insets <lsd.jar> <class.out> "
        "| strip-frames <class>")


if __name__ == "__main__":
    main(sys.argv)
