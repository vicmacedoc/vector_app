# Vector Brain — desktop

A small Windows app that mirrors the phone's **Brain**: Memory tiles, Model (Learn) tiles, Motion lists and to-dos, the Supermarket list, and the Inbox. It syncs through the same Google Drive folder as the phone (`Data/brain/_sync/state.json`) and asks you how to merge when both sides changed.

Stack: Tauri 2 (Rust shell) · React 19 · TypeScript · Tailwind 4 · Zustand.

## Prerequisites (one-time)

| Tool | Why | Get it |
| --- | --- | --- |
| Node.js 20+ | Frontend build | <https://nodejs.org> |
| Rust (stable, MSVC toolchain) | Native shell | <https://rustup.rs> → `rustup default stable-x86_64-pc-windows-msvc` |
| **Visual Studio Build Tools** with the **"Desktop development with C++"** workload | Rust needs the MSVC linker (`link.exe`) and Windows SDK | <https://visualstudio.microsoft.com/visual-cpp-build-tools/> |
| WebView2 runtime | Renders the UI | Preinstalled on Windows 10/11 |

If `cargo build` fails with ``linker `link.exe` not found``, the Build Tools workload is missing. Install it, then open a new terminal.

## Google Drive access (one-time)

The desktop app signs in with its own OAuth client of type **Desktop app**. Using the same Google Cloud project as the phone keeps everything in one place.

1. Open [Google Cloud Console](https://console.cloud.google.com/) and select the project the phone app uses.
2. **APIs & Services → Library**: make sure **Google Drive API** is enabled.
3. **APIs & Services → OAuth consent screen**:
   - Add the scope `https://www.googleapis.com/auth/drive`. The app needs full Drive access because it reads and writes files the phone created.
   - While the app is in **Testing**, add your Google account under **Test users**.
4. **APIs & Services → Credentials → Create credentials → OAuth client ID**:
   - Application type: **Desktop app**. Name it something like "Vector Brain desktop".
   - Copy the **Client ID** and **Client secret**.
5. Copy `.env.example` to `.env` in this folder and fill in both values:

   ```env
   VITE_GOOGLE_CLIENT_ID=1234567890-abc.apps.googleusercontent.com
   VITE_GOOGLE_CLIENT_SECRET=GOCSPX-...
   ```

   Google documents desktop client secrets as non-confidential, but `.env` is still git-ignored. The values are compiled into the app, so rebuild after changing them.

Sign-in opens your browser and returns to the app through a loopback redirect (`http://127.0.0.1:<random port>`), protected with PKCE. The refresh token is stored in **Windows Credential Manager** (entry `vector-desktop`). Access tokens are kept in memory only.

> While the consent screen is in *Testing*, Google expires refresh tokens after 7 days. When that happens the app shows **Signed out**; sign in again from Settings. Publishing the consent screen removes the limit.

## Run

```powershell
cd desktop
npm install
npm run app:dev      # desktop window with hot reload (compiles Rust on first run)
```

Other scripts:

| Command | What it does |
| --- | --- |
| `npm run app:build` | Release build plus installers in `src-tauri/target/release/bundle/` (`nsis/*.exe` per-user installer, `msi/*.msi`) |
| `npm test` | Vitest: merge engine against the shared `../sync-fixtures`, Motion scoring, Supermarket math, Brain operations |
| `npm run typecheck` | `tsc --noEmit` |
| `npm run dev` | UI only in a normal browser at <http://localhost:1420> (data stays in `localStorage`, Drive disabled). Useful for layout work |
| `npm run icons` | Regenerates `src-tauri/icons/*` from `app-icon.png` (square crop of `assets/logo.png`) |

## First run

1. **Settings → Sign in with Google.**
2. **Settings → Data folder → Find the phone's folder.** This searches Drive for `brain/_sync/state.json` and lists the matching `Data` folders. You can also paste the folder link or ID that the phone uses.
3. The first sync downloads the phone's Brain. If this computer already has local edits, the merge dialog opens.

After that, sync runs on launch, when the window regains focus (at most every 30 s), every 5 minutes, and about 8 s after each edit. The chip in the header shows the state; click it to sync now.

## Merging

Each side keeps the last state both agreed on (the *base*). On every sync the app compares base, local and Drive:

- Only one side changed: it is pushed or pulled silently.
- Both changed: the dialog offers **Keep this computer**, **Keep phone**, or **Smart merge**.
  - Smart merge auto-combines non-overlapping edits.
  - For each real conflict it shows the two versions side by side. You pick one, or keep both for notes and to-dos.

The rules are shared with the phone (`docs/brain-sync-spec.md`), and both test suites run the same fixtures.

**Settings → Compare from scratch** forgets the base, so the next sync treats every difference as a decision.

## Files and attachments

- Files you add (picker, drag and drop, or quick capture) go to `%APPDATA%\com.vm.vector.desktop\cache\` and are uploaded to the same Drive folders the phone uses:
  - `brain/Memory/<tile>/` for Memory tiles
  - `brain/Model/<tileId>/` for Model tiles
  - `brain/Inbox/` for the Inbox
- Opening an attachment that the phone added downloads it into the same cache first.
- Deleting a source moves its Drive file to the trash before the state is pushed. That way, the phone's folder scan doesn't re-import it.
- Local state lives in `%APPDATA%\com.vm.vector.desktop\`:
  - `working.json`: your current Brain
  - `base.json`: the last agreed state
  - `pending_remote.json`: an unresolved merge
  - `outbox.json`: pending uploads and trash

## Quick capture

Press **Ctrl+Shift+Space** anywhere, or use the sidebar button, to open a small always-on-top window. It can capture:

- a note (the first line is the title)
- a link
- a to-do with priority and due date
- a new Learn tile
- dropped files

**Ctrl+Enter** saves and **Esc** hides the window. Captures land in the Inbox, except Learn tiles, which go to Model.

## What the desktop does not do

- **No automatic Learn status changes.** "Review due" and "On hold" are shown, but only the phone writes those status transitions, so the two devices never fight over them.
- **No AI features** (voice capture, Gemini digests). Use the phone for those.
- Supermarket items are edited in place. A trip is recorded only when you confirm **"Did you go?"** after restocking.
