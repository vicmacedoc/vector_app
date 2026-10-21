// Account (Google sign-in + Drive folder) and Sync (Brain + daily shards) sections.
import { useEffect, useState } from "react";
import { AlertTriangle, CalendarDays, Cloud, FolderSearch, LogIn, LogOut, RefreshCw, RotateCcw } from "lucide-react";
import { cn } from "@/lib/cn";
import { hasClientId } from "@/drive/auth";
import { folderIdFromInput } from "@/drive/client";
import { errorMessage } from "@/platform/native";
import { pendingUploads } from "@/sync/engine";
import { useBrain } from "@/store/brain";
import { useDaily } from "@/store/daily";
import { Button, Confirm, Input } from "@/ui/primitives";
import { Row, SettingsSection } from "./common";

export function AccountSection() {
  const { signedIn, settings } = useBrain();
  const { signIn, signOut, detectFolders, setRootFolder } = useBrain.getState();
  const [found, setFound] = useState<{ id: string; name: string }[] | null>(null);
  const [detecting, setDetecting] = useState(false);
  const [link, setLink] = useState("");

  const detect = async () => {
    setDetecting(true);
    try {
      const folders = await detectFolders();
      setFound(folders);
      if (folders.length === 1 && !settings.rootFolderId) await setRootFolder(folders[0].id, folders[0].name);
    } catch (e) {
      useBrain.getState().toast(errorMessage(e), "error");
    } finally {
      setDetecting(false);
    }
  };

  const useLink = async () => {
    const id = folderIdFromInput(link);
    if (!id) {
      useBrain.getState().toast("That doesn't look like a Drive folder link", "error");
      return;
    }
    await setRootFolder(id, null);
    setLink("");
  };

  return (
    <SettingsSection id="account" title="Account" hint="Everything syncs through the same Drive folder as the phone.">
      {!hasClientId() && (
        <div className="mb-4 rounded-lg bg-danger/10 px-3 py-2 text-sm text-danger">
          No OAuth client configured. Add VITE_GOOGLE_CLIENT_ID to desktop/.env and rebuild (see README).
        </div>
      )}
      <Row label="Google account" hint="Drive for sync, Calendar (read-only) for the Routine import.">
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-2 text-sm">
            <Cloud size={16} className={signedIn ? "text-electric" : "text-item"} />
            {signedIn ? "Signed in" : "Not signed in"}
          </div>
          {signedIn ? (
            <Button variant="secondary" size="sm" onClick={() => void signOut()}><LogOut size={14} /> Sign out</Button>
          ) : (
            <Button size="sm" disabled={!hasClientId()} onClick={() => void signIn()}><LogIn size={14} /> Sign in with Google</Button>
          )}
        </div>
        {signedIn && (
          <div className="mt-2 flex items-start gap-1.5 text-xs text-item">
            <CalendarDays size={13} className="mt-0.5 shrink-0" />
            Signed in before Calendar import existed? Sign out and in again once to grant Calendar access.
          </div>
        )}
      </Row>
      {signedIn && (
        <Row label="Data folder" hint="The Drive folder whose ID is set on the phone (Settings → Drive).">
          <div className="mb-3 text-sm">
            {settings.rootFolderId ? (
              <span>Using <b>{settings.rootFolderName ?? "folder"}</b> <span className="font-mono text-xs text-item">{settings.rootFolderId}</span></span>
            ) : (
              <span className="text-muted">Not chosen yet.</span>
            )}
          </div>
          <Button variant="secondary" size="sm" disabled={detecting} onClick={() => void detect()}>
            <FolderSearch size={14} /> {detecting ? "Looking…" : "Find the phone's folder"}
          </Button>
          {found && (
            <div className="mt-3 space-y-1">
              {found.length === 0 && <div className="text-sm text-muted">No synced data found yet. Sync once on the phone, or paste the folder link below.</div>}
              {found.map((f) => (
                <button key={f.id} onClick={() => void setRootFolder(f.id, f.name)} className="flex w-full items-center justify-between rounded-lg border border-hairline px-3 py-2 text-left text-sm hover:border-electric">
                  <span>{f.name}</span>
                  <span className="font-mono text-xs text-item">{f.id === settings.rootFolderId ? "in use" : f.id}</span>
                </button>
              ))}
            </div>
          )}
          <div className="mt-3 flex gap-2">
            <Input value={link} onChange={(e) => setLink(e.target.value)} placeholder="…or paste the Drive folder link / ID" />
            <Button variant="secondary" onClick={() => void useLink()} disabled={!link.trim()}>Use</Button>
          </div>
        </Row>
      )}
    </SettingsSection>
  );
}

const shardLabel = (s: string) => (s === "config" ? "Settings & libraries" : s.startsWith("days/") ? new Date(`${s.slice(5)}-01T00:00:00`).toLocaleDateString(undefined, { month: "long", year: "numeric" }) : s);

export function SyncSection() {
  const { signedIn, settings, phase, syncMessage } = useBrain();
  const { setDeviceName, syncNow, resetSyncBase, openMerge } = useBrain.getState();
  const daily = useDaily();
  const [device, setDevice] = useState(settings.deviceName);
  const [uploads, setUploads] = useState(0);
  const [confirmReset, setConfirmReset] = useState(false);
  const ready = signedIn && !!settings.rootFolderId;

  useEffect(() => {
    void pendingUploads().then(setUploads);
  }, [phase]);
  useEffect(() => setDevice(settings.deviceName), [settings.deviceName]);

  const dirtyShards = Object.keys(daily.dirty).sort();
  const busy = phase === "syncing" || daily.phase === "syncing";

  return (
    <SettingsSection id="sync" title="Sync" hint="Runs on launch, on window focus, every minute, and a few seconds after each edit. You're only asked when the same field changed on both devices.">
      <Row label="Brain" hint="Notes, tiles, to-dos and the supermarket list.">
        <div className="flex flex-wrap items-center gap-3 text-sm">
          <span className="text-muted">
            {settings.lastSyncAt ? `Last synced ${new Date(settings.lastSyncAt).toLocaleString()}` : "Never synced"}
            {uploads > 0 && ` · ${uploads} file(s) waiting to upload`}
          </span>
          {phase === "conflict" && <Button size="sm" variant="accent" onClick={() => void openMerge()}>Review merge</Button>}
        </div>
        {syncMessage && phase === "error" && <div className="mt-2 text-sm text-danger">{syncMessage}</div>}
      </Row>
      <Row label="Calendar & Home" hint="Daily data in monthly files, plus one file for settings and libraries.">
        <div className="text-sm text-muted">
          {daily.lastSyncAt ? `Last synced ${new Date(daily.lastSyncAt).toLocaleString()}` : "Never synced"}
          {dirtyShards.length > 0 && ` · ${dirtyShards.length} file(s) with local changes`}
        </div>
        {daily.message && daily.phase === "error" && <div className="mt-2 text-sm text-danger">{daily.message}</div>}
        {daily.conflicts.length > 0 && (
          <div className="mt-3 space-y-1">
            {daily.conflicts.map((c) => (
              <button key={c} onClick={() => void daily.openMerge(c)} className="flex w-full items-center gap-2 rounded-lg border border-prio-mid/40 bg-prio-mid/5 px-3 py-2 text-left text-sm hover:border-prio-mid">
                <AlertTriangle size={14} className="text-prio-mid" /> {shardLabel(c)} needs a merge decision
              </button>
            ))}
          </div>
        )}
        {dirtyShards.length > 0 && (
          <div className="mt-3 flex flex-wrap gap-1.5">
            {dirtyShards.map((s) => <span key={s} className="rounded-full bg-offwhite px-2 py-0.5 text-[11px] text-muted">{shardLabel(s)}</span>)}
          </div>
        )}
      </Row>
      <Row label="Actions">
        <div className="flex flex-wrap gap-2">
          <Button size="sm" disabled={!ready || busy} onClick={() => { void syncNow(); void daily.syncNow(true); }}>
            <RefreshCw size={14} className={cn(busy && "animate-spin-slow")} /> Sync now
          </Button>
          <Button size="sm" variant="secondary" disabled={!ready} onClick={() => setConfirmReset(true)}>
            <RotateCcw size={14} /> Compare from scratch
          </Button>
        </div>
        <div className="mt-1 text-xs text-item">Compare from scratch forgets the last agreed state; the next sync compares both sides fully. Nothing is deleted.</div>
      </Row>
      <Row label="This computer's name" hint="Shown on the phone when it asks you to merge.">
        <Input value={device} onChange={(e) => setDevice(e.target.value)} onBlur={() => device.trim() && device !== settings.deviceName && void setDeviceName(device)} />
      </Row>
      <Confirm
        open={confirmReset}
        title="Compare from scratch?"
        message="If both this computer and Drive have content, you'll be asked how to merge them. Nothing is deleted by this step."
        confirmLabel="Start over"
        danger={false}
        onConfirm={() => { void resetSyncBase(); void daily.resetSyncBase(); }}
        onClose={() => setConfirmReset(false)}
      />
    </SettingsSection>
  );
}
