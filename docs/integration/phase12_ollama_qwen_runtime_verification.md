# Phase 12: Ollama and Qwen2.5-VL Runtime Verification Report

**Project**: FireSphere.ai  
**Workspace**: `C:\Users\athar\OneDrive\Desktop\Firesphere.ai`  
**AI Service Subsystem**: `C:\Users\athar\OneDrive\Desktop\Firesphere.ai\ai-service`  
**Execution Timestamp**: 2026-10-12T23:33:00+05:30  
**Phase**: Phase 12 — Local VLM Runtime Verification and Diagnostic  
**Final Verdict**: `PASS_RUNTIME_AND_IMAGE_INFERENCE`

---

## 1. Objective and Scope

The objective of Phase 12 was to perform safe, read-only runtime verification of the local Ollama daemon and the selected multimodal vision-language model (`qwen2.5vl:3b`) on the local host. This verification ensures that the local execution environment is capable of local multimodal inference before proceeding to Phase 13 (M4 Ollama Provider Integration).

### Scope Boundaries Enforced
- **Strictly local**: No requests were dispatched to Gemini, OpenAI, or any remote AI API.
- **Read-only application state**: Zero changes to existing application source code (`ai-service/app`), configurations, or `.env` files.
- **Zero storage writes**: Zero database transactions or MinIO object writes were performed.
- **Isolated synthetic test**: Exactly one text check and exactly one synthetic geometric image inference check were performed. No real citizen, building, or inspection evidence was used.
- **Clean state preservation**: All test images were synthesized and passed entirely in-memory (`io.BytesIO`), leaving no lingering test files in the workspace.

---

## 2. Ollama Installation and Local Service Status

Diagnostics verified the availability, version, and running service daemon of Ollama on the local machine:

| Diagnostic Metric | Observed Value | Status |
| :--- | :--- | :--- |
| **Executable Name** | `ollama` (`C:\Users\athar\AppData\Local\Programs\Ollama\ollama.exe`) | Detected & Functional |
| **Installed Version** | `0.40.3` | Verified (`ollama version is 0.40.3`) |
| **Local Daemon Process** | `ollama.exe` (PID 22928), `ollama app.exe` (PID 8396) | Running |
| **API Endpoint** | `http://127.0.0.1:11434` | Reachable & Responsive |
| **Version Endpoint (`/api/version`)** | `{"version": "0.40.3"}` | HTTP 200 OK |
| **User Access** | Full local REST access from current user context | Verified |

---

## 3. Selected Model Presence and Metadata

The model registry was queried through `ollama list` and the local REST API (`/api/tags`):

| Model Attribute | Recorded Value |
| :--- | :--- |
| **Exact Identifier** | `qwen2.5vl:3b` |
| **Digest** | `fb90415cde1ef08aa669ae74b082d49b158729b6db1ab183c941417d507e71a1` |
| **File Format** | `gguf` |
| **Architecture Family** | `qwen25vl` |
| **Parameter Size** | `3.8B` |
| **Quantization Level** | `Q4_K_M` |
| **Reported Size** | `3,200,627,168 bytes` (~3.2 GB on disk, 2.9 GB active) |
| **Context Length** | `128,000 tokens` |
| **Embedding Length** | `2048` |
| **Runner Backend** | `llamacpp` (`llama-server.exe`) |
| **Capabilities** | `["completion", "vision"]` |

The selected model `qwen2.5vl:3b` is installed, intact, and locally available without requiring additional downloads.

---

## 4. Text-Generation Test Result

A single text-completion request was dispatched to `http://127.0.0.1:11434/api/generate` with a bounded timeout:
- **Prompt**: `"Describe the purpose of a fire extinguisher in one sentence."`
- **Streaming**: Disabled (`stream: false`)

### Results
- **Execution Status**: `SUCCESS`
- **HTTP Code**: 200
- **Total Latency**: `8.35 seconds`
  - Model Load Duration: `7.33 seconds` (initial offload of model weights to GPU VRAM)
  - Prompt Evaluation: `31 tokens`
  - Response Generation: `37 tokens` in `0.637 seconds` (~58.1 tokens/sec)
- **Model Output**:
  > *"A fire extinguisher is a device used to put out fires by using a combination of water, foam, or other chemicals to suppress the flames and prevent the spread of the fire."*
- **Done Flag**: `true`
- **Output Quality**: Direct, coherent, syntactically complete single sentence.

---

## 5. Synthetic Image-Inference Test Result

Exactly one image-understanding request was submitted to the local `qwen2.5vl:3b` endpoint.

### Test Image Specification
- **Type**: Clearly synthetic geometric canvas generated in-memory.
- **Dimensions**: `400 x 400 pixels` (RGB PNG, 2,471 bytes).
- **Content**: Plain light-gray background with a red vertical rectangular cylinder, top black handle/lever, small circular pressure gauge, flexible nozzle line, and central yellow informational label.
- **Compliance Claim**: None (pure geometric representation; not presented as real inspection evidence).
- **Prompt**: `"Describe only the visible objects and their visible characteristics in this synthetic test image."`

### Results
- **Execution Status**: `SUCCESS`
- **Total Latency**: `13.44 seconds`
  - Model Load Duration: `14.24 milliseconds` (reused existing GPU allocation)
  - Vision & Prompt Token Evaluation: `1,060 tokens`
  - Token Generation: `43 tokens` in `0.631 seconds` (~68.1 tokens/sec)
- **Response Nonempty**: `true`
- **Model Output**:
  > *"The image depicts a red fire extinguisher. The extinguisher has a yellow label with black text and lines on it. The nozzle of the extinguisher is extended, pointing upwards. The background is plain white."*
- **Error Information**: `null`

### Behavioral and Visual Grounding Analysis
- The model correctly identified the synthetic object as representing a fire extinguisher.
- It correctly recognized the red body, the yellow label with black lines, and the nozzle.
- It maintained neutral descriptive grounding without hallucinating statutory violations, building codes, or inspection findings not requested.

---

## 6. Hardware and Runtime Resource Usage

Hardware telemetry was captured using `nvidia-smi` and Ollama runtime telemetry (`ollama ps`):

| Resource / Telemetry | Pre-Inference | During / Post-Inference | Diagnostics Source |
| :--- | :--- | :--- | :--- |
| **GPU Model** | NVIDIA GeForce RTX 4050 Laptop GPU | NVIDIA GeForce RTX 4050 Laptop GPU | `nvidia-smi` |
| **Total GPU VRAM** | 6,141 MiB (~6.0 GB) | 6,141 MiB (~6.0 GB) | `nvidia-smi` |
| **VRAM Allocated to Ollama** | 0 MiB | 4,083 MiB (text) / 4,143 MiB (vision) | `nvidia-smi` (`llama-server.exe`) |
| **Available Free VRAM** | 6,141 MiB | 1,778 MiB | `nvidia-smi` |
| **GPU Utilization Peak** | 0% | 94% during vision token eval | `nvidia-smi` |
| **Processor Offload** | N/A | **100% GPU** | `ollama ps` |
| **Active Context Window** | N/A | 4,096 tokens | `ollama ps` |
| **Active Runner Process** | None | `PID 28900: ...\lib\ollama\llama-server.exe` | Windows Process Table |
| **Host System RAM** | 16.0 GB visible (~3.4 GB free) | Maintained stable | Windows CIM OS Metrics |
| **Out-Of-Memory (OOM) Errors** | None | None | Application & Driver Logs |

### Hardware Conclusion
The model runs with **100% GPU offload** on the NVIDIA GeForce RTX 4050 Laptop GPU. Model weights and KV context fit comfortably within the 6 GB VRAM envelope (leaving ~1.78 GB free headroom), preventing CPU fallback or system memory thrashing.

---

## 7. Workspace and Application Integrity

Strict safety guardrails were observed throughout Phase 12:
- **Application Files**: No files in `ai-service/app`, `ai-service/tests`, or root directories were modified.
- **Configuration & Environment**: Neither `.env` nor `.env.example` was touched or dumped.
- **Databases & MinIO**: Zero write requests or database schema queries were executed.
- **Git State**: No git commits, resets, branch alterations, or repository updates were attempted.
- **File Modifications**: Workspace-wide filesystem scan confirmed zero modifications to existing application files during this phase.

---

## 8. Known Limitations

1. **Context Window Constraint**: Default runner allocates 4,096 tokens on GPU. High-resolution multi-image prompts will consume higher prompt token budgets and may require explicit num_ctx configuration during provider initialization.
2. **Single-Sample Verification**: This phase verified runtime mechanical capability (model loading, text generation, and vision prompt evaluation). It does not constitute a statistical evaluation of fire code accuracy or compliance judgment reliability.
3. **Model Warm-up Latency**: Initial cold-load time is ~7.3 seconds. Once loaded in VRAM, subsequent vision prompt evaluations execute in ~13 seconds with generation speeds >65 tokens/sec.

---

## 9. Recommendation for Phase 13

**Final Verdict**: `PASS_RUNTIME_AND_IMAGE_INFERENCE`

All prerequisite criteria for Phase 13 are satisfied:
1. Ollama v0.40.3 is operational and responding locally.
2. `qwen2.5vl:3b` is present, verified, and loads 100% into GPU VRAM.
3. Text generation and local vision inference completed successfully without error.
4. No external network dependencies, database writes, or source code modifications occurred.

**Next Step**:
Proceed to **Phase 13: M4 Ollama Provider Integration**, implementing the local Ollama provider inside the existing M4 provider abstraction layer while keeping CV approval gates and confidence calibration settings safe.
