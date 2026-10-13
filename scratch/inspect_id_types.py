import os
import re

# Check M2 ID types
m2_entities = [
    r"M2_work\Firesphere m2\backend\src\main\java\com\firesphere\backend\auth\entity\User.java",
    r"M2_work\Firesphere m2\backend\src\main\java\com\firesphere\backend\organization\entity\Organization.java",
    r"M2_work\Firesphere m2\backend\src\main\java\com\firesphere\backend\noc\entity\NocApplication.java",
    r"M2_work\Firesphere m2\backend\src\main\java\com\firesphere\backend\inspection\entity\NocInspection.java",
    r"M2_work\Firesphere m2\backend\src\main\java\com\firesphere\backend\ai\entity\AiAnalysisRun.java"
]

print("=== M2 Entity ID Types ===")
for p in m2_entities:
    if os.path.exists(p):
        with open(p, 'r', encoding='utf-8', errors='ignore') as fp:
            c = fp.read()
            m = re.findall(r'@Id\s+(?:@GeneratedValue[^\n]*\s+)?(?:private|protected)\s+([A-Za-z0-9_]+)\s+id', c)
            print(f"{os.path.basename(p)}: {m}")

# Check M3 column types in migrations
print("\n=== M3 Column Types in Migrations ===")
m3_mig_path = r"M3_work\IP_DB\database\migrations"
for f in ["V1.0.1__core_tenancy_and_identities.sql", "V1.0.3__operations_noc_and_inspections.sql"]:
    p = os.path.join(m3_mig_path, f)
    with open(p, 'r', encoding='utf-8', errors='ignore') as fp:
        c = fp.read()
        for t in ["organizations", "users", "noc_applications", "inspections"]:
            pattern = rf'CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?firesphere\.{t}\s*\((.*?)\);'
            m = re.search(pattern, c, re.DOTALL | re.IGNORECASE)
            if m:
                body = m.group(1)
                lines = [line.strip() for line in body.split('\n') if line.strip() and not line.strip().startswith('--')]
                id_lines = [l for l in lines if l.lower().startswith('id ') or l.lower().startswith('org_id ') or l.lower().startswith('application_id ')]
                print(f"{t}: {id_lines[:3]}")
