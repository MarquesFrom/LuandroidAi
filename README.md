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
- ComfyUI API workflows for image generation, editing and image-to-video
- Import of ComfyUI API-format workflows, with optional placeholders:
  `{{PROMPT}}`, `{{IMAGE_FILENAME}}`, `{{IMAGE_SUBFOLDER}}`, `{{IMAGE_TYPE}}`
- Local gallery storage and opening generated images/videos in Android apps
- Optional API key encrypted with the Android Keystore
- No extra content-moderation layer is added by this client; the selected
  engine, model, host or cloud provider may still enforce its own policies

For ComfyUI, export a workflow in **API format** and put the placeholders in
the appropriate prompt and Load Image node inputs. Use the image-to-video mode
with a workflow that accepts a reference image and returns a video output.
Each engine may have different nodes, model requirements and output types.

For OpenAI-compatible engines, the first implementation uses
`/v1/models`, `/v1/images/generations` and `/v1/images/edits`. Support depends
on the engine and model implementing those routes; chat-only models cannot
generate images. Video creation in this app currently uses the ComfyUI adapter.

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
[`LuandroidAi-0.1.0-debug.apk`](artifacts/LuandroidAi-0.1.0-debug.apk).
This is a debug-signed build for testing, not a production release. Android may
ask you to allow installation from your browser or file manager.
SHA-256: `12a76d9814c1e8fdcab5cfc966e8661c8cc84bf7e511dbc56d9167dafd3ae294`

## Scope and licensing

The initial Android milestone implements the visual-creation shell and engine
adapters; it does not yet port the desktop app's complete chat, agent,
benchmark, voice, model-management, remote-access or computer-control
features. Android executes the UI and transfers selected images; the configured
engine performs the heavy generation. This keeps the app practical for
mid-range phones while allowing generation capacity to depend on the chosen
server or service.

The upstream project is licensed under AGPL-3.0. This port is intended to
remain AGPL-3.0; retain the license and provide corresponding source when
distributing builds. See the [upstream license](https://github.com/PurpleDoubleD/locally-uncensored/blob/v3.0.6/LICENSE).
