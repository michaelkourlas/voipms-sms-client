import json
import sys

licenseMap = {
    'Android Software Development Kit License': 'Android-SDK.txt',
    'Apache License 2.0': 'Apache-2.0.txt',
    'Apache License, Version 2.0': 'Apache-2.0.txt',
    'The Apache License, Version 2.0': 'Apache-2.0.txt',
    'The Apache Software License, Version 2.0': 'Apache-2.0.txt',
}

licenseGroupMap = {
    ('MIT License', 'com.github.mukeshsolanki', 'MarkdownView-Android', '2.0.0'): 'MarkdownView-Android.txt'
}

dependencies = json.load(open(f'build/generated/oss_licenses/{sys.argv[1]}/dependencies_with_licenses.json'))
dependencies.sort(key=lambda e: e['name'])
for entry in dependencies:
    for l in entry['licenses']:
        if l['text'] is None:
            fileName = None
            if l['name'] in licenseMap:
                fileName = licenseMap[l['name']]
            else:
                artifactInfo = entry['artifactInfo']
                if artifactInfo is not None:
                    key = (l['name'], artifactInfo['group'], artifactInfo['name'], artifactInfo['version'])
                    if key in licenseGroupMap:
                        fileName = licenseGroupMap[key]
            if fileName is not None:
                l['text'] = open(f'../licenses/{fileName}').read()
            else:
                raise Exception(entry)

with open('src/main/assets/PACKAGE_LICENSES.md', 'w', encoding='utf-8') as f:
    for entry in dependencies:
        artifactInfo = entry['artifactInfo']
        if artifactInfo is not None:
            f.write(f'## {entry["name"]} ({artifactInfo["group"]}:{artifactInfo["name"]}:{artifactInfo["version"]})')
        else:
            f.write(f'## {entry["name"]}')
        for l in entry['licenses']:
            f.write('\n\n```\n')
            f.write(l['text'].strip('\n'))
            f.write('\n```\n\n')
