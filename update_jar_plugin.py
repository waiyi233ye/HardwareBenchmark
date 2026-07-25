#!/usr/bin/env python3
"""Update plugin.yml in a JAR file without extracting everything."""
import sys
import zipfile
import shutil
import os

def update_jar_plugin_yml(jar_path, new_plugin_yml_path):
    """Replace plugin.yml in jar with new content."""
    tmp_path = jar_path + '.tmp'
    with zipfile.ZipFile(jar_path, 'r') as zin:
        with zipfile.ZipFile(tmp_path, 'w', zipfile.ZIP_DEFLATED) as zout:
            for item in zin.infolist():
                if item.filename == 'plugin.yml':
                    # Replace with new content
                    with open(new_plugin_yml_path, 'rb') as f:
                        zout.writestr(item, f.read())
                else:
                    # Copy as-is
                    data = zin.read(item.filename)
                    zout.writestr(item, data)
    shutil.move(tmp_path, jar_path)
    print(f"Updated {jar_path}")

if __name__ == '__main__':
    jar_path = sys.argv[1]
    plugin_yml_path = sys.argv[2]
    update_jar_plugin_yml(jar_path, plugin_yml_path)
