# LuandroidAi

LuandroidAi is a native Android app for Ollama chat and visual creation through
a ComfyUI server. The app runs on the phone; model inference and image
generation run on servers reachable over the network.

This Android-focused port is inspired by
[Locally Uncensored](https://github.com/PurpleDoubleD/locally-uncensored),
release v3.0.6. The Android app is not yet at full feature parity with the
desktop app.

## Ollama chat and models

Chat uses Ollama's native `/api/chat` endpoint. The app is preconfigured for
`qwen3:8b` and lets the user switch to `gemma2:2b`. Both are text models and
must already be installed on the connected Ollama server. If needed, install
them there with:

```sh
ollama pull qwen3:8b
ollama pull gemma2:2b
```

The server address defaults to `http://10.0.2.2:11434` and ComfyUI to
`http://10.0.2.2:8188`, which reach the host computer from the Android
emulator. The server processes must be running and reachable. On a physical
phone, the host address is different and the server must accept LAN requests.

## Image creation, cloning and video

Ollama does not generate or edit images. Image generation, reference-image
cloning/editing and image-to-video use ComfyUI's API. In Motor, select the
workflow JSON exported from ComfyUI in **API format**. Its selection is saved
automatically. Use `{{PROMPT}}` and the image placeholders
`{{IMAGE_FILENAME}}`, `{{IMAGE_SUBFOLDER}}`, `{{IMAGE_TYPE}}`,
`{{IMAGE2_FILENAME}}`, `{{IMAGE2_SUBFOLDER}}` and `{{IMAGE2_TYPE}}` in the
corresponding workflow nodes. Optional sampler placeholders are `{{SEED}}`,
`{{STEPS}}`, `{{CFG}}`, `{{WIDTH}}`, `{{HEIGHT}}`, `{{DENOISE}}`,
`{{SAMPLER}}`, `{{SCHEDULER}}` and `{{NEGATIVE_PROMPT}}`.

The create screen supports up to two reference images. Choose **Clonar /
editar**, attach one or two references and describe what to change or preserve.
The Ollama chat can prepare a creation and queue it for explicit confirmation
in the app before opening the create screen. Qwen3 and Gemma 2 2B are text-only;
use the Create screen to attach images to a visual workflow. The ComfyUI
workflow must have matching image input nodes and compatible installed models.

## Other implemented features

- Portuguese native Android interface with Create, Chat, Gallery and Engine tabs
- Qwen 3 chat by default, with a one-tap switch to Gemma 2 2B
- Assistant chat with confirmation-gated visual creation requests
- Local gallery storage and opening generated media in Android apps
- ComfyUI workflow selection that is saved automatically
- No cloud API keys are required by the app

HTTP is enabled for local-network engines. HTTP does not protect prompts or
images in transit; use HTTPS for remote servers and do not expose an
unauthenticated ComfyUI or Ollama server directly to the public internet.

## Build

Install JDK 17 or newer, Android SDK Platform 36, and Android Build Tools 36.
Then run:

```sh
./gradlew :app:assembleDebug
```

The resulting debug APK is written to `app/build/outputs/apk/debug/`.
For a ready-to-install test build, download
[`LuandroidAi-0.2.0-debug.apk`](artifacts/LuandroidAi-0.2.0-debug.apk).
This is a debug-signed build for testing, not a production release. Android may
ask you to allow installation from your browser or file manager.
SHA-256: `d8015e3c6baf7aa8d3f8989310f62f1aa0b9e56b8daead05afe5cfbd1c721c30`

## Scope and licensing

The app implements visual creation through ComfyUI and Ollama chat/model
management; it does not port the desktop app's benchmark, voice, remote-access
or computer-control features. Android runs the interface and transfers selected
images; the configured servers perform the inference and generation.

The upstream project is licensed under AGPL-3.0. This port is intended to
remain AGPL-3.0; retain the license and provide corresponding source when
distributing builds. See the
[upstream license](https://github.com/PurpleDoubleD/locally-uncensored/blob/v3.0.6/LICENSE).
