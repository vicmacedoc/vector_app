# Brain sync protocol (v1)

Phone (Android, Room) and desktop (Tauri) keep Brain in sync through one JSON file on Google Drive.
Both sides implement the same 3-way merge; `sync-fixtures/` holds the shared test cases that both
the Kotlin (`mobile/src/test/.../BrainMergeFixturesTest.kt`) and TypeScript (`desktop/src/sync/merge.test.ts`)
suites must pass.

## Files

| Where | What |
|---|---|
| Drive `Data/brain/_sync/state.json` | The shared state (below). Overwritten in place; Drive keeps revisions. |
| Device-local `base.json` | The last state this device and Drive agreed on (pushed or pulled). |
| Device-local `pending_remote.json` | Remote state waiting for a merge decision. |
| Drive `Data/brain/{Inbox,Memory,Model}/...` | Media binaries. Never embedded in `state.json`. |
| Drive `Data/brain/Motion/Supermarket.json` | Export-only copy of the Supermarket list for humans. Not read by sync. |

## state.json

```json
{
  "formatVersion": 1,
  "rev": 42,
  "writtenBy": { "deviceId": "uuid", "name": "Pixel 9", "platform": "android" },
  "writtenAt": 1759600000000,
  "settings": { "motionDailyTarget": 50, "supermarketDay": "SUNDAY" },
  "nodes": [], "notes": [], "assets": [], "learningTiles": [], "learningLinks": [],
  "tags": [], "tasks": [], "scoreEvents": [],
  "supermarket": { "name": "Supermarket", "sections": [], "items": [] }
}
```

- `rev` increases by one on every push. A reader that sees `rev` different from its base knows Drive changed.
- Records are written **complete**: every field, explicit `null`s, enums by name, timestamps in epoch millis.
  Field names and meaning match the Room entities in `core/src/main/java/com/vm/core/models`:

| Array | Entity | Key |
|---|---|---|
| `nodes` | `BrainNode` (MEMORY / MODEL / MOTION tiles) | `id` |
| `notes` | `MemoryNote` (sources; `nodeId: null` = Inbox) | `id` |
| `assets` | `BrainAsset` minus device-local `localRelativePath`, `syncState`; plus `path` | `id` |
| `learningTiles` | `LearningTile` (same `id` as its MODEL node) | `id` |
| `learningLinks` | `LearningLink` (`fromNodeId` depends on `toNodeId`) | `fromNodeId + "|" + toNodeId` |
| `tags` | `BrainTag` | `id` |
| `tasks` | `MotionTask` | `id` |
| `scoreEvents` | `MotionScoreEvent` | `id` |
| `supermarket.items` | `VectorItem` with `id: ""` | `title.trim().lowercase()` (+ `#2`, `#3` for duplicates) |

`assets[].path` is the file location under Drive `brain/` (for example `Memory/Books/cover.jpg`,
`Model/node_123_ab12cd34/notes.pdf`, `Inbox/voice.m4a`). Devices download by `driveFileId` and may store the
file anywhere locally.

New IDs use `{prefix}_{epochMillis}_{8 hex chars}` with prefixes `node`, `note`, `asset`, `task`, `tag`.

## Merge input: record sets

Before merging, a state is flattened into a **record set**: `collection -> key -> record (JSON object)`.
Collections: `nodes, notes, assets, learningTiles, learningLinks, tags, tasks, scoreEvents, supermarketItems, meta`.
`meta` holds two records: `settings` (the settings object) and `supermarket` (`{ name, sections }`).

Equality is deep JSON equality where numbers compare by value (`0` equals `0.0`) and a missing field equals `null`.

## Change detection

- `localChanged` = canonical(local) differs from canonical(base). Canonical form sorts collections, keys and
  object fields; with no base, local counts as changed when it has any record.
- `remoteChanged` = Drive has a state and (no base, or `remote.rev != base.rev`).

| local | remote | action |
|---|---|---|
| no | no | nothing |
| yes | no | push local as `rev = remote.rev + 1` (or `base.rev + 1` / `1`) |
| no | yes | apply remote locally, base = remote |
| yes | yes | ask the user: **Keep this device**, **Keep the other device**, or **Smart merge** |

Before pushing, re-read the Drive file's `version`; if it moved since it was read, start over.

## 3-way merge (per record)

For every key in local, remote or base (iteration order: local keys, then remote-only, then base-only):

1. `local == remote` -> take it (covers both deleted, or both made the same edit).
2. `local == base` -> take remote. 3. `remote == base` -> take local.
4. Otherwise both changed it:
   - both present -> **field merge** against base (base absent -> every field's base is `null`):
     per field, same rule 1-3; fields that differ on both sides are *conflicting fields*.
     No conflicting fields -> auto-merged. Otherwise conflict `EDIT_EDIT` (or `ADD_ADD` when base was absent).
   - local deleted, remote edited -> conflict `DELETE_EDIT`.
   - local edited, remote deleted -> conflict `EDIT_DELETE`.

Conflict id: `collection + "/" + key`.

### Collection policies

- `scoreEvents` never surface conflicts: an edited record beats a deleted one; between two edits the higher
  `createdAt` wins (tie -> local).
- **Keep both** is offered only for `notes` and `tasks`.

## Resolutions

| Conflict | `LOCAL` | `REMOTE` | `BOTH` |
|---|---|---|---|
| `EDIT_EDIT`, `ADD_ADD` | field-merged record, conflicting fields from local | ... from remote | `LOCAL` result **plus** the full remote record under key `key + "_copy"` (`_copy2`, ... if taken), its `id` field set to the new key |
| `DELETE_EDIT` | deleted | remote record | remote record |
| `EDIT_DELETE` | local record | deleted | local record |

Global choices: **Keep this device** = the local record set as-is; **Keep the other device** = the remote record set
as-is; **Smart merge** = auto-merged records + per-conflict resolutions (default `LOCAL`).

## After resolving (all three choices)

1. **Duplicate Drive files.** Assets sharing a non-null `driveFileId` collapse to one: keep the one whose key exists
   in remote, else the smallest key. Notes pointing at a dropped asset are repointed to the kept one. Then among notes
   with the same (`assetId`, `nodeId`, `kind`) keep one (key in remote first, else smallest key).
2. **Integrity.** With `N` = node keys: notes/tasks whose `nodeId` is not in `N` move to the Inbox (`null`);
   tasks' `linkedTileId` not in `N` becomes `null`; `learningTiles` whose key is not in `N` are dropped;
   `learningLinks` whose `fromNodeId` or `toNodeId` is not in `N` are dropped.

## Fixtures

Each file in `sync-fixtures/` is:

```json
{
  "description": "...",
  "base": { "<collection>": { "<key>": { } } },
  "local": { }, "remote": { },
  "expect": {
    "localChanged": true, "remoteChanged": true,
    "conflicts": [ { "id": "notes/note_1", "kind": "EDIT_EDIT", "fields": ["title"] } ],
    "resolvedLocal": { }, "resolvedRemote": { }, "resolvedBoth": { }
  }
}
```

`remoteChanged` in fixtures means `canonical(remote) != canonical(base)` (fixtures carry no `rev`).
`resolvedLocal` / `resolvedRemote` apply Smart merge with every conflict resolved `LOCAL` / `REMOTE`;
`resolvedBoth` (optional) uses `BOTH` where allowed and `LOCAL` elsewhere. Results compare in canonical form;
missing collections are empty; `fields` compare as sorted lists.
