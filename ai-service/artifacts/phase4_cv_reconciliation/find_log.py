import json

transcript_path = r"C:\Users\athar\.gemini\antigravity-ide\brain\9e846ae3-7298-4ebe-bd90-5133c708bbaa\.system_generated\logs\transcript_full.jsonl"

with open(transcript_path, "r", encoding="utf-8") as f:
    for idx, line in enumerate(f):
        if idx in [601, 602, 604, 605, 606]:
            obj = json.loads(line)
            print(f"=== LINE {idx} Step {obj.get('step_index')} ===")
            print("Status:", obj.get("status"))
            print("Content:", str(obj.get("content"))[:400])
