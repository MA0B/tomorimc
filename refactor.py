import os
import shutil

replacements = {
    'com.example.examplemod': 'com.tomorimc',
    'examplemod': 'tomorimc',
    'ExampleMod': 'TomoriMC',
    '"id": "mod_id"': '"id": "tomorimc"'
}

for root, dirs, files in os.walk('.'):
    if '.git' in root: continue
    for file in files:
        if file.endswith(('.java', '.json', '.toml', '.gradle')):
            path = os.path.join(root, file)
            try:
                with open(path, 'r', encoding='utf-8') as f:
                    content = f.read()
                new_content = content
                for k, v in replacements.items():
                    new_content = new_content.replace(k, v)
                if new_content != content:
                    with open(path, 'w', encoding='utf-8') as f:
                        f.write(new_content)
            except Exception as e:
                pass

for root, dirs, files in os.walk('.', topdown=False):
    for d in dirs:
        if d == 'examplemod':
            old_path = os.path.join(root, d)
            new_path = os.path.join(root, 'tomorimc')
            os.rename(old_path, new_path)

for root, dirs, files in os.walk('.', topdown=False):
    for d in dirs:
        if d == 'example':
            old_path = os.path.join(root, d)
            tomorimc_path = os.path.join(old_path, 'tomorimc')
            if os.path.exists(tomorimc_path):
                new_path = os.path.join(root, 'tomorimc')
                if not os.path.exists(new_path):
                    shutil.move(tomorimc_path, new_path)
                try:
                    os.rmdir(old_path)
                except:
                    pass
