import json

transcript_path = r"C:\Users\athar\.gemini\antigravity-ide\brain\9e846ae3-7298-4ebe-bd90-5133c708bbaa\.system_generated\logs\transcript_full.jsonl"

with open(transcript_path, "r", encoding="utf-8") as f:
    lines = [json.loads(l) for l in f]

msg1 = lines[608]
msg2 = lines[609]

c1 = msg1.get("content", "")
c2 = msg2.get("content", "")

print("=== MESSAGE 1 (Step 608, timestamp 11:13:06Z) ===")
print("Full length:", len(c1))
print(c1)

print("\n=== MESSAGE 2 (Step 609, timestamp 11:12:42Z) ===")
print("Full length:", len(c2))
print(c2)
