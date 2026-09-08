import glob
import xml.etree.ElementTree as ET
from collections import defaultdict


def check_xml(file_path):
    tree = ET.parse(file_path)
    root = tree.getroot()
    name_cache = defaultdict(int)

    for elem in root:
        name = elem.attrib.get('name')
        if name:
            name_cache[name] += 1

    for name, count in name_cache.items():
        if count > 1:
            print(f"{file_path}: {name} - {count} occurrences")

search_path = 'app/src/main/res/**/values-*/strings.xml'
for file_path in glob.glob(search_path, recursive=True):
    check_xml(file_path)
