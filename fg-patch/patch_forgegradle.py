#!/usr/bin/env python3
"""
Patch ForgeGradle 2.3 .class files to redirect calls to the removed
Jar.setClassifier/setBaseName/setVersion methods to static JarCompat methods.

Strategy:
  For each call site:
    invokevirtual Jar.setX(String)V  ->  invokestatic JarCompat.setX(Jar, String)V

  The JVM stack layout for invokevirtual is: [obj, arg] -> []
  For invokestatic with signature (Jar, String)V it's: [obj, arg] -> []
  So the stack is identical - we only change the opcode and the methodref.
"""
import sys
import struct
import os
import shutil
import zipfile

# Constants
CONSTANT_Utf8 = 1
CONSTANT_Class = 7
CONSTANT_Methodref = 10
CONSTANT_InterfaceMethodref = 11
CONSTANT_NameAndType = 12
INVOKEVIRTUAL = 0xb6
INVOKESTATIC = 0xb8
INVOKEINTERFACE = 0xb9

# Target method refs to redirect
# (owner_class_internal_name, method_name, method_descriptor)
TARGETS = [
    ("org/gradle/api/tasks/bundling/Jar", "setClassifier", "(Ljava/lang/String;)V"),
    ("org/gradle/jvm/tasks/Jar",          "setClassifier", "(Ljava/lang/String;)V"),
    ("org/gradle/api/tasks/bundling/Jar", "setBaseName",   "(Ljava/lang/String;)V"),
    ("org/gradle/api/tasks/bundling/Jar", "setVersion",    "(Ljava/lang/String;)V"),
    ("org/gradle/api/tasks/bundling/Jar", "setAppendix",   "(Ljava/lang/String;)V"),
    ("org/gradle/api/tasks/bundling/Jar", "setExtension",  "(Ljava/lang/String;)V"),
    ("org/gradle/api/tasks/bundling/Jar", "setArchiveName", "(Ljava/lang/String;)V"),
    # Zip is the parent of Jar; ForgeGradle also calls setX on Zip directly
    ("org/gradle/api/tasks/bundling/Zip", "setClassifier", "(Ljava/lang/String;)V"),
    ("org/gradle/api/tasks/bundling/Zip", "setBaseName",   "(Ljava/lang/String;)V"),
    ("org/gradle/api/tasks/bundling/Zip", "setVersion",    "(Ljava/lang/String;)V"),
    ("org/gradle/api/tasks/bundling/Zip", "setAppendix",   "(Ljava/lang/String;)V"),
    ("org/gradle/api/tasks/bundling/Zip", "setExtension",  "(Ljava/lang/String;)V"),
    ("org/gradle/api/tasks/bundling/Zip", "setArchiveName", "(Ljava/lang/String;)V"),
    # JavaExec.setMain(String)JavaExec - removed in Gradle 8, replaced by getMainClass().set()
    ("org/gradle/api/tasks/JavaExec", "setMain", "(Ljava/lang/String;)Lorg/gradle/api/tasks/JavaExec;"),
]

# Interface method redirects: change the owner class and/or descriptor of
# invokeinterface calls where the Gradle 8 API changed.
# (old_owner, method_name, old_descriptor, new_owner, new_descriptor)
INTERFACE_REDIRECTS = [
    # TaskOutputsInternal.dir(Object)TaskOutputs -> TaskOutputs.dir(Object)TaskOutputFilePropertyBuilder
    # (return value is always popped by ForgeGradle, so the return type change is safe)
    ("org/gradle/api/internal/TaskOutputsInternal", "dir",
     "(Ljava/lang/Object;)Lorg/gradle/api/tasks/TaskOutputs;",
     "org/gradle/api/tasks/TaskOutputs",
     "(Ljava/lang/Object;)Lorg/gradle/api/tasks/TaskOutputFilePropertyBuilder;"),
    # TaskOutputsInternal.upToDateWhen(Closure)V -> TaskOutputs.upToDateWhen(Closure)V
    ("org/gradle/api/internal/TaskOutputsInternal", "upToDateWhen",
     "(Lgroovy/lang/Closure;)V",
     "org/gradle/api/tasks/TaskOutputs",
     "(Lgroovy/lang/Closure;)V"),
]

# The compat class that will receive the redirected calls
COMPAT_CLASS = "com/hwbench/gradlecompat/JarCompat"


class ConstantPool:
    """Parses and allows modification of a .class file's constant pool."""

    def __init__(self, data):
        self.data = bytearray(data)
        self._parse()

    def _parse(self):
        pos = 8  # skip magic(4) + minor(2) + major(2)
        self.cp_count = struct.unpack('>H', self.data[pos:pos+2])[0]
        self.cp_start = pos
        pos += 2

        self.entries = {}  # index -> (tag, offset, size)
        i = 1
        while i < self.cp_count:
            tag = self.data[pos]
            entry_start = pos
            pos += 1
            if tag == CONSTANT_Utf8:
                length = struct.unpack('>H', self.data[pos:pos+2])[0]
                pos += 2 + length
            elif tag in (CONSTANT_Class, 8, 16, 19, 20):
                pos += 2
            elif tag in (CONSTANT_Methodref, CONSTANT_InterfaceMethodref, 9, 12, 17, 18):
                pos += 4
            elif tag in (3, 4):
                pos += 4
            elif tag in (5, 6):  # long/double take 2 slots
                pos += 8
                self.entries[i] = (tag, entry_start, pos - entry_start)
                i += 1
                continue
            elif tag == 15:
                pos += 3
            else:
                raise ValueError(f"Unknown constant pool tag {tag} at entry {i}, offset {entry_start}")
            self.entries[i] = (tag, entry_start, pos - entry_start)
            i += 1

        self.cp_end = pos
        self.code_start = pos

    def get_utf8(self, index):
        """Get the string value of a Utf8 constant pool entry."""
        if index not in self.entries:
            return None
        tag, offset, size = self.entries[index]
        if tag != CONSTANT_Utf8:
            return None
        length = struct.unpack('>H', self.data[offset+1:offset+3])[0]
        return self.data[offset+3:offset+3+length].decode('utf-8')

    def get_class_name(self, index):
        """Get the internal class name from a Class constant."""
        if index not in self.entries:
            return None
        tag, offset, size = self.entries[index]
        if tag != CONSTANT_Class:
            return None
        name_index = struct.unpack('>H', self.data[offset+1:offset+3])[0]
        return self.get_utf8(name_index)

    def get_methodref(self, index):
        """Get (class_name, method_name, descriptor) from a Methodref or InterfaceMethodref."""
        if index not in self.entries:
            return None
        tag, offset, size = self.entries[index]
        if tag not in (CONSTANT_Methodref, CONSTANT_InterfaceMethodref):
            return None
        class_index = struct.unpack('>H', self.data[offset+1:offset+3])[0]
        nat_index = struct.unpack('>H', self.data[offset+3:offset+5])[0]
        class_name = self.get_class_name(class_index)
        if class_name is None:
            return None
        # Parse NameAndType
        if nat_index not in self.entries:
            return None
        nat_tag, nat_offset, nat_size = self.entries[nat_index]
        if nat_tag != CONSTANT_NameAndType:
            return None
        name_idx = struct.unpack('>H', self.data[nat_offset+1:nat_offset+3])[0]
        desc_idx = struct.unpack('>H', self.data[nat_offset+3:nat_offset+5])[0]
        method_name = self.get_utf8(name_idx)
        descriptor = self.get_utf8(desc_idx)
        return (class_name, method_name, descriptor)

    def find_methodref(self, class_name, method_name, descriptor, ref_type=None):
        """Find the constant pool index of a specific methodref/interface methodref.
        If ref_type is None, searches both Methodref and InterfaceMethodref."""
        for idx, (tag, offset, size) in self.entries.items():
            if ref_type is not None and tag != ref_type:
                continue
            if ref_type is None and tag not in (CONSTANT_Methodref, CONSTANT_InterfaceMethodref):
                continue
            ref = self.get_methodref(idx)
            if ref == (class_name, method_name, descriptor):
                return idx
        return None

    def add_utf8(self, text):
        """Append a new Utf8 constant and return its index."""
        text_bytes = text.encode('utf-8')
        new_index = self.cp_count
        self.cp_count += 1
        entry = bytes([CONSTANT_Utf8]) + struct.pack('>H', len(text_bytes)) + text_bytes
        # Insert before code_start
        self.data[self.code_start:self.code_start] = bytearray(entry)
        # Update entries dict
        self.entries[new_index] = (CONSTANT_Utf8, self.code_start, len(entry))
        # Shift all offsets after the insertion point
        for idx, (tag, offset, size) in list(self.entries.items()):
            if idx != new_index and offset >= self.code_start:
                self.entries[idx] = (tag, offset + len(entry), size)
        self.code_start += len(entry)
        # Update cp_count in the data
        struct.pack_into('>H', self.data, self.cp_start, self.cp_count)
        return new_index

    def add_class(self, name_index):
        """Append a new Class constant pointing to a Utf8 name_index."""
        new_index = self.cp_count
        self.cp_count += 1
        entry = bytes([CONSTANT_Class]) + struct.pack('>H', name_index)
        self.data[self.code_start:self.code_start] = bytearray(entry)
        self.entries[new_index] = (CONSTANT_Class, self.code_start, len(entry))
        for idx, (tag, offset, size) in list(self.entries.items()):
            if idx != new_index and offset >= self.code_start:
                self.entries[idx] = (tag, offset + len(entry), size)
        self.code_start += len(entry)
        struct.pack_into('>H', self.data, self.cp_start, self.cp_count)
        return new_index

    def add_name_and_type(self, name_index, desc_index):
        """Append a new NameAndType constant."""
        new_index = self.cp_count
        self.cp_count += 1
        entry = bytes([CONSTANT_NameAndType]) + struct.pack('>HH', name_index, desc_index)
        self.data[self.code_start:self.code_start] = bytearray(entry)
        self.entries[new_index] = (CONSTANT_NameAndType, self.code_start, len(entry))
        for idx, (tag, offset, size) in list(self.entries.items()):
            if idx != new_index and offset >= self.code_start:
                self.entries[idx] = (tag, offset + len(entry), size)
        self.code_start += len(entry)
        struct.pack_into('>H', self.data, self.cp_start, self.cp_count)
        return new_index

    def add_methodref(self, class_index, nat_index):
        """Append a new Methodref constant."""
        new_index = self.cp_count
        self.cp_count += 1
        entry = bytes([CONSTANT_Methodref]) + struct.pack('>HH', class_index, nat_index)
        self.data[self.code_start:self.code_start] = bytearray(entry)
        self.entries[new_index] = (CONSTANT_Methodref, self.code_start, len(entry))
        for idx, (tag, offset, size) in list(self.entries.items()):
            if idx != new_index and offset >= self.code_start:
                self.entries[idx] = (tag, offset + len(entry), size)
        self.code_start += len(entry)
        struct.pack_into('>H', self.data, self.cp_start, self.cp_count)
        return new_index

    def add_interface_methodref(self, class_index, nat_index):
        """Append a new InterfaceMethodref constant."""
        new_index = self.cp_count
        self.cp_count += 1
        entry = bytes([CONSTANT_InterfaceMethodref]) + struct.pack('>HH', class_index, nat_index)
        self.data[self.code_start:self.code_start] = bytearray(entry)
        self.entries[new_index] = (CONSTANT_InterfaceMethodref, self.code_start, len(entry))
        for idx, (tag, offset, size) in list(self.entries.items()):
            if idx != new_index and offset >= self.code_start:
                self.entries[idx] = (tag, offset + len(entry), size)
        self.code_start += len(entry)
        struct.pack_into('>H', self.data, self.cp_start, self.cp_count)
        return new_index

    def get_or_create_methodref(self, class_name, method_name, descriptor):
        """Find or create a methodref pointing to a static method on COMPAT_CLASS.
        The new descriptor includes the original owner class as first parameter,
        preserving the original return type."""
        # descriptor is like "(Ljava/lang/String;)V" or "(Ljava/lang/String;)Lorg/gradle/...;"
        # New descriptor: "(L<owner_class>;<original_args>)<original_return>"
        # The args part is between the first '(' and the matching ')'
        orig_args = descriptor[1:descriptor.rindex(')')]
        orig_return = descriptor[descriptor.rindex(')')+1:]
        new_descriptor = f"(L{class_name};{orig_args}){orig_return}"
        # Check if we already created this methodref
        existing = self.find_methodref(COMPAT_CLASS, method_name, new_descriptor)
        if existing is not None:
            return existing

        # Create new Utf8 + Class + NameAndType + Methodref
        class_utf8 = self.add_utf8(COMPAT_CLASS)
        class_entry = self.add_class(class_utf8)
        name_utf8 = self.add_utf8(method_name)
        desc_utf8 = self.add_utf8(new_descriptor)
        nat_entry = self.add_name_and_type(name_utf8, desc_utf8)
        methodref = self.add_methodref(class_entry, nat_entry)
        return methodref

    def get_or_create_interface_methodref(self, new_owner, method_name, new_descriptor):
        """Find or create an InterfaceMethodref with the given owner/name/descriptor."""
        existing = self.find_methodref(new_owner, method_name, new_descriptor,
                                        ref_type=CONSTANT_InterfaceMethodref)
        if existing is not None:
            return existing

        class_utf8 = self.add_utf8(new_owner)
        class_entry = self.add_class(class_utf8)
        name_utf8 = self.add_utf8(method_name)
        desc_utf8 = self.add_utf8(new_descriptor)
        nat_entry = self.add_name_and_type(name_utf8, desc_utf8)
        methodref = self.add_interface_methodref(class_entry, nat_entry)
        return methodref

    def get_bytes(self):
        return bytes(self.data)


def patch_bytecode_opcodes(data, cp, target_methodref_indices, replacement_indices,
                            iface_redirect_indices=None, iface_replacements=None):
    """Scan method bytecode for invoke instructions referencing the target
    methodref indices and change them.

    - target_methodref_indices: invokevirtual targets to change to invokestatic
    - iface_redirect_indices: invokeinterface targets to redirect to new methodrefs
    """
    if iface_redirect_indices is None:
        iface_redirect_indices = set()
    if iface_replacements is None:
        iface_replacements = {}

    # Skip past the constant pool to the access_flags
    pos = cp.code_start
    access_flags = struct.unpack('>H', data[pos:pos+2])[0]
    pos += 2
    this_class = struct.unpack('>H', data[pos:pos+2])[0]
    pos += 2
    super_class = struct.unpack('>H', data[pos:pos+2])[0]
    pos += 2
    interfaces_count = struct.unpack('>H', data[pos:pos+2])[0]
    pos += 2 + interfaces_count * 2
    fields_count = struct.unpack('>H', data[pos:pos+2])[0]
    pos += 2
    for _ in range(fields_count):
        pos += 6  # access_flags(2) + name_index(2) + descriptor_index(2)
        attrs_count = struct.unpack('>H', data[pos:pos+2])[0]
        pos += 2
        for _ in range(attrs_count):
            pos += 2  # attribute_name_index
            attr_len = struct.unpack('>I', data[pos:pos+4])[0]
            pos += 4 + attr_len

    methods_count = struct.unpack('>H', data[pos:pos+2])[0]
    pos += 2

    patched_count = 0
    for _ in range(methods_count):
        pos += 6  # access_flags + name_index + descriptor_index
        attrs_count = struct.unpack('>H', data[pos:pos+2])[0]
        pos += 2
        for _ in range(attrs_count):
            attr_name_idx = struct.unpack('>H', data[pos:pos+2])[0]
            pos += 2
            attr_len = struct.unpack('>I', data[pos:pos+4])[0]
            attr_data_start = pos + 4
            attr_name = cp.get_utf8(attr_name_idx)

            if attr_name == "Code":
                # Code attribute: max_stack(2) + max_locals(2) + code_length(4) + code + ...
                code_start = attr_data_start + 8
                code_length = struct.unpack('>I', cp.data[attr_data_start+4:attr_data_start+8])[0]
                code_end = code_start + code_length

                i = code_start
                while i < code_end:
                    opcode = cp.data[i]
                    if opcode == INVOKEVIRTUAL:
                        ref_idx = struct.unpack('>H', cp.data[i+1:i+3])[0]
                        if ref_idx in target_methodref_indices:
                            # Patch: change opcode to invokestatic and update methodref index
                            new_idx = replacement_indices[ref_idx]
                            cp.data[i] = INVOKESTATIC
                            struct.pack_into('>H', cp.data, i+1, new_idx)
                            patched_count += 1
                    elif opcode == INVOKEINTERFACE:
                        ref_idx = struct.unpack('>H', cp.data[i+1:i+3])[0]
                        if ref_idx in iface_redirect_indices:
                            # Patch: update the interface methodref index (opcode stays invokeinterface)
                            new_idx = iface_replacements[ref_idx]
                            struct.pack_into('>H', cp.data, i+1, new_idx)
                            patched_count += 1
                    i += 1

            pos = attr_data_start + attr_len

    return patched_count


def patch_class_file(class_path):
    """Patch a single .class file to redirect Jar.setX calls to JarCompat.setX
    and fix TaskOutputsInternal interface method refs."""
    with open(class_path, 'rb') as f:
        original = f.read()

    cp = ConstantPool(original)

    # ---- Part 1: Jar/Zip.setX -> JarCompat.setX (invokevirtual -> invokestatic) ----
    target_indices = {}
    replacement_indices = {}

    for (class_name, method_name, descriptor) in TARGETS:
        idx = cp.find_methodref(class_name, method_name, descriptor)
        if idx is not None:
            target_indices[idx] = (class_name, method_name, descriptor)
            print(f"  Found methodref #{idx}: {class_name}.{method_name}{descriptor}")

    if target_indices:
        for idx, (class_name, method_name, descriptor) in target_indices.items():
            new_idx = cp.get_or_create_methodref(class_name, method_name, descriptor)
            replacement_indices[idx] = new_idx
            new_desc = f"(L{class_name};{descriptor[1:]}"
            print(f"  Created replacement #{idx} -> #{new_idx}: {COMPAT_CLASS}.{method_name}{new_desc}")

    # ---- Part 2: Interface method redirects (invokeinterface) ----
    iface_redirect_indices = set()
    iface_replacements = {}

    for (old_owner, method_name, old_desc, new_owner, new_desc) in INTERFACE_REDIRECTS:
        idx = cp.find_methodref(old_owner, method_name, old_desc,
                                ref_type=CONSTANT_InterfaceMethodref)
        if idx is not None:
            iface_redirect_indices.add(idx)
            new_idx = cp.get_or_create_interface_methodref(new_owner, method_name, new_desc)
            iface_replacements[idx] = new_idx
            print(f"  Found interface methodref #{idx}: {old_owner}.{method_name}{old_desc}")
            print(f"  Redirected #{idx} -> #{new_idx}: {new_owner}.{method_name}{new_desc}")

    if not target_indices and not iface_redirect_indices:
        return 0

    # Patch the bytecode
    patched = patch_bytecode_opcodes(cp.data, cp,
                                      set(target_indices.keys()), replacement_indices,
                                      iface_redirect_indices, iface_replacements)

    if patched > 0:
        with open(class_path, 'wb') as f:
            f.write(cp.get_bytes())
        print(f"  Patched {patched} call site(s) in {class_path}")

    return patched


# Fields in ForgeGradle task classes that need @Internal annotation to pass
# Gradle 8's strict task property validation.
# (class_file_internal_path, field_name)
FIELDS_NEEDING_INTERNAL = [
    ("net/minecraftforge/gradle/user/TaskDepDummy.class", "outputFile"),
    ("net/minecraftforge/gradle/tasks/ExtractConfigTask.class", "includes"),
    ("net/minecraftforge/gradle/tasks/ExtractConfigTask.class", "excludes"),
    ("net/minecraftforge/gradle/tasks/ExtractConfigTask.class", "outputFile"),
    ("net/minecraftforge/gradle/tasks/AbstractEditJarTask.class", "outputFile"),
    ("net/minecraftforge/gradle/tasks/ObtainFernFlowerTask.class", "outputFile"),
    ("net/minecraftforge/gradle/tasks/EtagDownloadTask.class", "outputFile"),
    ("net/minecraftforge/gradle/tasks/ExtractS2SRangeTask.class", "outputFile"),
    ("net/minecraftforge/gradle/tasks/ExtractMCPTask.class", "outputFile"),
    ("net/minecraftforge/gradle/tasks/McpCleanupTask.class", "outputFile"),
]


def add_internal_annotation_to_field(class_path, field_name):
    """Add @Internal annotation to a field in a .class file.

    The @Internal annotation tells Gradle 8 to ignore this property during
    up-to-date checking, fixing the "missing input or output annotation" error.
    """
    with open(class_path, 'rb') as f:
        data = bytearray(f.read())

    cp = ConstantPool(data)

    # Find or create the Utf8 entries we need
    internal_desc = "Lorg/gradle/api/tasks/Internal;"
    runtime_visible_annotations_name = "RuntimeVisibleAnnotations"

    # Find existing Utf8 entries
    internal_utf8_idx = None
    rva_utf8_idx = None
    for idx, (tag, offset, size) in cp.entries.items():
        if tag != CONSTANT_Utf8:
            continue
        s = cp.get_utf8(idx)
        if s == internal_desc:
            internal_utf8_idx = idx
        if s == runtime_visible_annotations_name:
            rva_utf8_idx = idx

    if internal_utf8_idx is None:
        internal_utf8_idx = cp.add_utf8(internal_desc)
    if rva_utf8_idx is None:
        rva_utf8_idx = cp.add_utf8(runtime_visible_annotations_name)

    # Now find the field and add the annotation
    # Skip past constant pool to access_flags
    pos = cp.code_start
    pos += 2  # access_flags
    pos += 2  # this_class
    pos += 2  # super_class
    interfaces_count = struct.unpack('>H', cp.data[pos:pos+2])[0]
    pos += 2 + interfaces_count * 2

    fields_count = struct.unpack('>H', cp.data[pos:pos+2])[0]
    pos += 2

    found = False
    for _ in range(fields_count):
        field_start = pos
        access_flags = struct.unpack('>H', cp.data[pos:pos+2])[0]
        name_idx = struct.unpack('>H', cp.data[pos+2:pos+4])[0]
        desc_idx = struct.unpack('>H', cp.data[pos+4:pos+6])[0]
        attrs_count = struct.unpack('>H', cp.data[pos+6:pos+8])[0]
        attrs_start = pos + 8

        field_name_utf8 = cp.get_utf8(name_idx)
        if field_name_utf8 == field_name or field_name == "*":
            found = True
            # Build the RuntimeVisibleAnnotations attribute
            # annotation entry: type_index(2) + num_element_value_pairs(2)
            annotation_entry = struct.pack('>HH', internal_utf8_idx, 0)
            # RuntimeVisibleAnnotations: num_annotations(2) + annotations[]
            rva_body = struct.pack('>H', 1) + annotation_entry
            # attribute_info: attribute_name_index(2) + attribute_length(4) + body
            rva_attr = struct.pack('>HI', rva_utf8_idx, len(rva_body)) + rva_body

            # Check if RuntimeVisibleAnnotations already exists for this field
            attr_pos = attrs_start
            has_rva = False
            for _ in range(attrs_count):
                attr_name = struct.unpack('>H', cp.data[attr_pos:attr_pos+2])[0]
                attr_len = struct.unpack('>I', cp.data[attr_pos+2:attr_pos+6])[0]
                attr_name_str = cp.get_utf8(attr_name)
                if attr_name_str == runtime_visible_annotations_name:
                    has_rva = True
                    break
                attr_pos += 6 + attr_len

            if not has_rva:
                # Insert the new attribute after the existing field attributes
                insert_pos = attrs_start
                for _ in range(attrs_count):
                    attr_name = struct.unpack('>H', cp.data[insert_pos:insert_pos+2])[0]
                    attr_len = struct.unpack('>I', cp.data[insert_pos+2:insert_pos+6])[0]
                    insert_pos += 6 + attr_len

                # Insert the RuntimeVisibleAnnotations attribute
                cp.data[insert_pos:insert_pos] = bytearray(rva_attr)
                # Increment attributes_count
                new_attrs_count = attrs_count + 1
                struct.pack_into('>H', cp.data, field_start + 6, new_attrs_count)
                print(f"  Added @Internal to field '{field_name_utf8}' in {class_path}")
                # Update attrs_count for this iteration so we skip past the new attribute
                attrs_count = new_attrs_count

        # Skip past this field's attributes
        attr_pos = attrs_start
        for _ in range(attrs_count):
            attr_len = struct.unpack('>I', cp.data[attr_pos+2:attr_pos+6])[0]
            attr_pos += 6 + attr_len
        pos = attr_pos

    if not found:
        if field_name != "*":
            print(f"  WARNING: Field '{field_name}' not found in {class_path}")
        return False

    with open(class_path, 'wb') as f:
        f.write(cp.get_bytes())
    return True


# ---- Gradle 8 task validation fixes ----
#
# Gradle 8 enforces strict task property validation:
#  1. Fields annotated with @Input/@Output/etc. MUST have a corresponding getter
#     (getX/isX). Fields without a getter but WITH annotations cause a hard error.
#  2. Public getter methods (getX/isX) that represent task properties MUST have
#     an input/output annotation. Getters without any annotation cause a hard error.
#
# ForgeGradle 2.3 was written for Gradle 4-5 which didn't enforce these rules.
# We fix both issues by:
#  - Removing ALL RuntimeVisibleAnnotations from fields that lack a getter
#  - Adding @Internal to public getter methods that have no annotation


def _parse_methods(cp):
    """Parse the method table of a class file. Returns a list of dicts:
    [{access_flags, name, descriptor, attrs_offset, attrs_count, method_start}, ...]
    Does NOT modify the data.
    """
    pos = cp.code_start
    pos += 2  # access_flags
    pos += 2  # this_class
    pos += 2  # super_class
    interfaces_count = struct.unpack('>H', cp.data[pos:pos+2])[0]
    pos += 2 + interfaces_count * 2

    fields_count = struct.unpack('>H', cp.data[pos:pos+2])[0]
    pos += 2
    for _ in range(fields_count):
        pos += 6  # access_flags + name_index + descriptor_index
        attrs_count = struct.unpack('>H', cp.data[pos:pos+2])[0]
        pos += 2
        for _ in range(attrs_count):
            pos += 2  # attribute_name_index
            attr_len = struct.unpack('>I', cp.data[pos:pos+4])[0]
            pos += 4 + attr_len

    methods_count = struct.unpack('>H', cp.data[pos:pos+2])[0]
    pos += 2

    methods = []
    for _ in range(methods_count):
        method_start = pos
        access_flags = struct.unpack('>H', cp.data[pos:pos+2])[0]
        name_idx = struct.unpack('>H', cp.data[pos+2:pos+4])[0]
        desc_idx = struct.unpack('>H', cp.data[pos+4:pos+6])[0]
        attrs_count = struct.unpack('>H', cp.data[pos+6:pos+8])[0]
        attrs_offset = pos + 8
        name = cp.get_utf8(name_idx)
        descriptor = cp.get_utf8(desc_idx)
        methods.append({
            'access_flags': access_flags,
            'name': name,
            'descriptor': descriptor,
            'attrs_offset': attrs_offset,
            'attrs_count': attrs_count,
            'method_start': method_start,
        })
        # Skip past this method's attributes
        attr_pos = attrs_offset
        for _ in range(attrs_count):
            attr_len = struct.unpack('>I', cp.data[attr_pos+2:attr_pos+6])[0]
            attr_pos += 6 + attr_len
        pos = attr_pos

    return methods


def _has_runtime_visible_annotations(cp, attrs_offset, attrs_count):
    """Check if a field/method has a RuntimeVisibleAnnotations attribute."""
    attr_pos = attrs_offset
    for _ in range(attrs_count):
        attr_name_idx = struct.unpack('>H', cp.data[attr_pos:attr_pos+2])[0]
        attr_len = struct.unpack('>I', cp.data[attr_pos+2:attr_pos+6])[0]
        attr_name = cp.get_utf8(attr_name_idx)
        if attr_name == "RuntimeVisibleAnnotations":
            return True
        attr_pos += 6 + attr_len
    return False


def _getter_name_for_field(field_name):
    """Return the getter method names that correspond to a field name.
    For 'foo' -> ['getFoo', 'isFoo']. For 'patternSet' -> ['getPatternSet', 'isPatternSet'].
    """
    if not field_name:
        return []
    cap = field_name[0].upper() + field_name[1:]
    return ['get' + cap, 'is' + cap]


def remove_annotations_from_fields_without_getters(class_path):
    """Remove ALL RuntimeVisibleAnnotations from ALL fields.

    In Gradle 8, field annotations are only used as a fallback when there's no
    getter. Since we add @Internal to all unannotated getters, the getter
    annotation always takes precedence. Leaving annotations on fields can cause
    conflicts (e.g., @Internal on field + @Internal on getter = duplicate) or
    errors (field with @Input but no getter). Removing all field annotations
    is the safest approach: the getter annotations handle everything.
    """
    with open(class_path, 'rb') as f:
        data = bytearray(f.read())

    cp = ConstantPool(data)
    runtime_visible_annotations_name = "RuntimeVisibleAnnotations"

    # Parse fields
    pos = cp.code_start
    pos += 2  # access_flags
    pos += 2  # this_class
    pos += 2  # super_class
    interfaces_count = struct.unpack('>H', cp.data[pos:pos+2])[0]
    pos += 2 + interfaces_count * 2

    fields_count = struct.unpack('>H', cp.data[pos:pos+2])[0]
    pos += 2

    removed_count = 0
    # Process fields in reverse order so byte deletions don't affect earlier positions
    field_infos = []
    for _ in range(fields_count):
        field_start = pos
        access_flags = struct.unpack('>H', cp.data[pos:pos+2])[0]
        name_idx = struct.unpack('>H', cp.data[pos+2:pos+4])[0]
        desc_idx = struct.unpack('>H', cp.data[pos+4:pos+6])[0]
        attrs_count = struct.unpack('>H', cp.data[pos+6:pos+8])[0]
        attrs_offset = pos + 8
        field_name = cp.get_utf8(name_idx)
        field_infos.append((field_start, field_name, attrs_offset, attrs_count))
        # Skip past attributes
        attr_pos = attrs_offset
        for _ in range(attrs_count):
            attr_len = struct.unpack('>I', cp.data[attr_pos+2:attr_pos+6])[0]
            attr_pos += 6 + attr_len
        pos = attr_pos

    for field_start, field_name, attrs_offset, attrs_count in reversed(field_infos):
        # Find and remove the RuntimeVisibleAnnotations attribute
        attr_pos = attrs_offset
        new_attrs_count = attrs_count
        for _ in range(attrs_count):
            attr_name_idx = struct.unpack('>H', cp.data[attr_pos:attr_pos+2])[0]
            attr_len = struct.unpack('>I', cp.data[attr_pos+2:attr_pos+6])[0]
            attr_name = cp.get_utf8(attr_name_idx)
            attr_end = attr_pos + 6 + attr_len
            if attr_name == runtime_visible_annotations_name:
                # Remove this attribute
                del cp.data[attr_pos:attr_end]
                new_attrs_count -= 1
                struct.pack_into('>H', cp.data, field_start + 6, new_attrs_count)
                removed_count += 1
                print(f"  Removed RuntimeVisibleAnnotations from field '{field_name}' in {class_path}")
                break
            attr_pos = attr_end

    if removed_count > 0:
        with open(class_path, 'wb') as f:
            f.write(cp.get_bytes())

    return removed_count


def add_internal_to_unannotated_getters(class_path):
    """Add @Internal annotation to public getter methods (getX/isX) that
    have no RuntimeVisibleAnnotations. This fixes Gradle 8's error:
      "property 'X' is missing an input or output annotation"
    """
    with open(class_path, 'rb') as f:
        data = bytearray(f.read())

    cp = ConstantPool(data)

    internal_desc = "Lorg/gradle/api/tasks/Internal;"
    runtime_visible_annotations_name = "RuntimeVisibleAnnotations"

    # Find or create Utf8 entries
    internal_utf8_idx = None
    rva_utf8_idx = None
    for idx, (tag, offset, size) in cp.entries.items():
        if tag != CONSTANT_Utf8:
            continue
        s = cp.get_utf8(idx)
        if s == internal_desc:
            internal_utf8_idx = idx
        if s == runtime_visible_annotations_name:
            rva_utf8_idx = idx

    if internal_utf8_idx is None:
        internal_utf8_idx = cp.add_utf8(internal_desc)
    if rva_utf8_idx is None:
        rva_utf8_idx = cp.add_utf8(runtime_visible_annotations_name)

    ACC_PUBLIC = 0x0001
    ACC_STATIC = 0x0008

    # Re-parse methods (constant pool may have changed if we added Utf8 entries)
    methods = _parse_methods(cp)

    added_count = 0
    # Process in reverse order so insertions don't affect earlier positions
    for m in reversed(methods):
        name = m['name']
        # Skip constructors, static methods, and non-getter names
        if name in ('<init>', '<clinit>'):
            continue
        if m['access_flags'] & ACC_STATIC:
            continue
        if not (m['access_flags'] & ACC_PUBLIC):
            continue
        is_getter = (name.startswith('get') and len(name) > 3 and name[3].isupper()) or \
                    (name.startswith('is') and len(name) > 2 and name[2].isupper())
        if not is_getter:
            continue
        # Skip methods that return void
        if m['descriptor'].endswith(')V'):
            continue

        # Check if this getter already has RuntimeVisibleAnnotations
        if _has_runtime_visible_annotations(cp, m['attrs_offset'], m['attrs_count']):
            continue

        # Add @Internal annotation
        # Build the RuntimeVisibleAnnotations attribute
        annotation_entry = struct.pack('>HH', internal_utf8_idx, 0)
        rva_body = struct.pack('>H', 1) + annotation_entry
        rva_attr = struct.pack('>HI', rva_utf8_idx, len(rva_body)) + rva_body

        # Find insertion point (after last attribute)
        insert_pos = m['attrs_offset']
        for _ in range(m['attrs_count']):
            attr_len = struct.unpack('>I', cp.data[insert_pos+2:insert_pos+6])[0]
            insert_pos += 6 + attr_len

        cp.data[insert_pos:insert_pos] = bytearray(rva_attr)
        new_attrs_count = m['attrs_count'] + 1
        struct.pack_into('>H', cp.data, m['method_start'] + 6, new_attrs_count)
        added_count += 1
        print(f"  Added @Internal to getter '{name}{m['descriptor']}' in {class_path}")

    if added_count > 0:
        with open(class_path, 'wb') as f:
            f.write(cp.get_bytes())

    return added_count


def patch_field_annotations(work_dir):
    """Fix Gradle 8 task property validation issues in ForgeGradle task classes.

    Strategy:
      1. Add @Internal to all public getter methods that have no annotation
         (fixes "property X is missing an input or output annotation")
      2. Remove ALL RuntimeVisibleAnnotations from ALL fields
         (fixes "field X without corresponding getter has been annotated with @Input"
         and avoids field/getter annotation conflicts)
    """
    # Scan ALL ForgeGradle task classes and apply comprehensive validation fixes
    # Use os.walk to recursively scan all subdirectories
    task_class_dirs = [
        os.path.join(work_dir, "net/minecraftforge/gradle/tasks"),
        os.path.join(work_dir, "net/minecraftforge/gradle/user"),
        os.path.join(work_dir, "net/minecraftforge/gradle/patcher"),
    ]
    for task_dir in task_class_dirs:
        if not os.path.isdir(task_dir):
            continue
        for root, dirs, files in os.walk(task_dir):
            for fname in sorted(files):
                if not fname.endswith('.class'):
                    continue
                path = os.path.join(root, fname)
                rel_path = os.path.relpath(path, work_dir)
                print(f"\n--- Fixing task validation for {rel_path} ---")
                # Step 1: Add @Internal to unannotated getter methods
                try:
                    add_internal_to_unannotated_getters(path)
                except Exception as e:
                    print(f"  ERROR adding @Internal to getters: {e}")
                # Step 2: Remove ALL annotations from ALL fields
                try:
                    remove_annotations_from_fields_without_getters(path)
                except Exception as e:
                    print(f"  ERROR removing field annotations: {e}")


def main():
    fg_jar = sys.argv[1]
    compat_class = sys.argv[2]  # path to JarCompat.class
    output_jar = sys.argv[3] if len(sys.argv) > 3 else fg_jar

    print(f"Patching ForgeGradle JAR: {fg_jar}")
    print(f"Adding compat class from: {compat_class}")

    # Extract to temp dir
    work_dir = "/tmp/fg-patch-work"
    if os.path.exists(work_dir):
        shutil.rmtree(work_dir)
    os.makedirs(work_dir)

    with zipfile.ZipFile(fg_jar, 'r') as zf:
        zf.extractall(work_dir)

    # Add JarCompat.class to the JAR
    compat_dest = os.path.join(work_dir, COMPAT_CLASS.replace('/', os.sep) + ".class")
    os.makedirs(os.path.dirname(compat_dest), exist_ok=True)
    shutil.copy2(compat_class, compat_dest)
    print(f"Added {compat_class} -> {compat_dest}")

    # Scan ALL class files for target methodrefs and patch them
    class_files = []
    for root, dirs, files in os.walk(work_dir):
        for file in files:
            if file.endswith('.class'):
                rel_path = os.path.relpath(os.path.join(root, file), work_dir)
                class_files.append(rel_path)

    total_patched = 0
    for target in sorted(class_files):
        path = os.path.join(work_dir, target)
        # Quick pre-check: does the file contain any target strings?
        with open(path, 'rb') as f:
            content = f.read()
        target_names = set(
            method_name for (_, method_name, _) in TARGETS
        ) | set(
            method_name for (_, method_name, _, _, _) in INTERFACE_REDIRECTS
        )
        has_target = any(
            name.encode('utf-8') in content
            for name in target_names
        )
        if not has_target:
            continue
        print(f"\nProcessing {target}:")
        try:
            total_patched += patch_class_file(path)
        except Exception as e:
            print(f"  ERROR: {e}")

    print(f"\nTotal call sites patched: {total_patched}")

    # Add @Internal annotations to fields that need them for Gradle 8 task validation
    patch_field_annotations(work_dir)

    # Repackage the JAR
    print(f"\nRepackaging to: {output_jar}")
    with zipfile.ZipFile(output_jar, 'w', zipfile.ZIP_DEFLATED) as zf:
        for root, dirs, files in os.walk(work_dir):
            for file in files:
                file_path = os.path.join(root, file)
                arcname = os.path.relpath(file_path, work_dir)
                zf.write(file_path, arcname)

    print("Done!")


if __name__ == '__main__':
    main()
