# LuandroidAi

LuandroidAi is a native Android app focused on creating and editing images and
turning a reference image into video. Its interface runs on the phone; image
and video inference is sent to a compatible engine reachable over the network,
so a phone does not have to load a large model into memory.

This project is an Android-focused port inspired by
[Locally Uncensored](https://github.com/PurpleDoubleD/locally-uncensored),
release v3.0.6. The Android app is not yet at full feature parity with the
desktop app.

## Implemented visual-creation flows

- Native Android interface in Portuguese, with Create, Gallery and Engine tabs
- Image generation and image editing through OpenAI-compatible image APIs
- Image editing with up to two reference images (where the selected endpoint supports it)
- ComfyUI API workflows for image generation, editing and image-to-video
- Import of ComfyUI API-format workflows, with optional placeholders:
  `{{PROMPT}}`, `{{IMAGE_FILENAME}}`, `{{IMAGE_SUBFOLDER}}`, `{{IMAGE_TYPE}}`,
  `{{IMAGE2_FILENAME}}`, `{{IMAGE2_SUBFOLDER}}`, `{{IMAGE2_TYPE}}`
- Presets for ComfyUI on a LAN, OpenAI image/chat endpoints, and custom
  OpenAI-compatible image and chat services
- Assistant chat with optional web search through a user-configured SearXNG
  instance; chat creation requests are queued for confirmation before use
- Local gallery storage and opening generated images/videos in Android apps
- Optional API key encrypted with the Android Keystore
- No extra content-moderation layer is added by this client; the selected
  engine, model, host or cloud provider may still enforce its own policies

For ComfyUI, export a workflow in **API format** and put the placeholders in
the appropriate prompt and Load Image node inputs. Use the image-to-video mode
with a workflow that accepts a reference image and returns a video output.
Each engine may have different nodes, model requirements and output types.

For OpenAI-compatible image engines, the app uses `/v1/models`,
`/v1/images/generations` and `/v1/images/edits`. The endpoint/model must support
the requested route and, for two-image edits, repeated `image[]` multipart
fields. Chat uses a separately configurable OpenAI-compatible
`/v1/chat/completions` endpoint with tool-call support. Optional web search
uses SearXNG's JSON search endpoint; provide a SearXNG base URL in Motor
settings. Video creation in this app currently uses the ComfyUI adapter.

The built-in OpenAI preset only fills in public endpoint and model identifiers.
It does not include an API key or pay for service usage. Enter your own
credentials; the image engine and chat may use separate keys. The ComfyUI LAN
preset uses `192.168.1.10` as an example address, which must be replaced with
the server's reachable LAN IP. Keep the optional chat endpoint configured
separately if you want to use an OpenAI-compatible chat while ComfyUI handles
image/video generation.

### Connecting a local engine

The engine must be reachable from the phone on the network. Use the computer's
LAN IP address, such as `http://192.168.1.20:8188` for ComfyUI or
`http://192.168.1.20:11434/v1` for an OpenAI-compatible API. `127.0.0.1` on
the phone means the phone itself, not another computer. Ensure the server is
configured to accept connections from the LAN and that its firewall allows
the connection.

HTTP is enabled to support local-network engines, but it does not protect
prompts or API keys in transit. Use HTTPS for remote endpoints, and do not
expose an unauthenticated generation server directly to the public internet.

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

To install it, download the APK on the phone, open it from Downloads, and
confirm Android's installation prompt. If asked, allow your browser or file
manager to install this test app.

## Scope and licensing

The Android app implements visual creation, a tool-enabled assistant chat and
optional SearXNG search; it does not port the desktop app's benchmark, voice,
model-management, remote-access or computer-control features. Android executes
the UI and transfers selected images; the configured engine performs the heavy
generation. This keeps the app practical for mid-range phones while allowing
generation capacity to depend on the chosen server or service.

The upstream project is licensed under AGPL-3.0. This port is intended to
remain AGPL-3.0; retain the license and provide corresponding source when
distributing builds. See the [upstream license](https://github.com/PurpleDoubleD/locally-uncensored/blob/v3.0.6/LICENSE).
