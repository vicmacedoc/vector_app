// Gemini key (kept in Windows Credential Manager by Rust), model override, and the Windows Hello gate.
import { useEffect, useState } from "react";
import { Eye, EyeOff, Fingerprint, KeyRound, Sparkles } from "lucide-react";
import { DEFAULT_MODEL, getModel, setModel, testKey } from "@/ai/gemini";
import { errorMessage, native, type GeminiKeySource } from "@/platform/native";
import { useBrain } from "@/store/brain";
import { usePrivacy } from "@/store/privacy";
import { Button, Input } from "@/ui/primitives";
import { Row, SettingsSection } from "./common";

const STATUS: Record<GeminiKeySource, string> = {
  custom: "Using custom key",
  build: "Using build key",
  none: "Not configured",
};

export function AiSection() {
  const toast = useBrain((s) => s.toast);
  const [status, setStatus] = useState<GeminiKeySource>("none");
  const [key, setKey] = useState("");
  const [show, setShow] = useState(false);
  const [model, setModelText] = useState(getModel());
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<{ ok: boolean; text: string } | null>(null);

  const refresh = () => void native.geminiStatus().then(setStatus).catch(() => setStatus("none"));
  useEffect(refresh, []);

  const save = async () => {
    try {
      await native.geminiKeySet(key);
      setKey("");
      setShow(false);
      toast("Gemini key saved");
      refresh();
    } catch (e) {
      toast(errorMessage(e), "error");
    }
  };

  const clear = async () => {
    try {
      await native.geminiKeyClear();
      toast("Custom key removed");
      refresh();
    } catch (e) {
      toast(errorMessage(e), "error");
    }
  };

  const test = async () => {
    setTesting(true);
    setTestResult(null);
    try {
      setTestResult({ ok: true, text: await testKey() });
    } catch (e) {
      setTestResult({ ok: false, text: errorMessage(e) });
    } finally {
      setTesting(false);
    }
  };

  return (
    <SettingsSection id="ai" title="AI" hint="Gemini powers the Calendar AI, voice-note transcripts, Learn helpers and Motion “From text”.">
      <Row label="Status">
        <div className="flex items-center gap-2 text-sm">
          <Sparkles size={15} className={status === "none" ? "text-item" : "text-electric"} />
          <span className={status === "none" ? "text-muted" : "font-medium"}>{STATUS[status]}</span>
        </div>
      </Row>
      <Row label="Gemini API key" hint="Stored in Windows Credential Manager on this PC only. Never synced, never shown again.">
        <div className="flex gap-2">
          <div className="relative flex-1">
            <Input
              type={show ? "text" : "password"}
              value={key}
              autoComplete="off"
              spellCheck={false}
              onChange={(e) => setKey(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && key.trim() && void save()}
              placeholder={status === "custom" ? "•••••••••• (saved)" : "Paste your key"}
              className="pr-9 font-mono"
            />
            <button type="button" className="absolute top-1/2 right-2 -translate-y-1/2 text-item hover:text-black" onClick={() => setShow((s) => !s)} title={show ? "Hide" : "Show"}>
              {show ? <EyeOff size={15} /> : <Eye size={15} />}
            </button>
          </div>
          <Button disabled={!key.trim()} onClick={() => void save()}><KeyRound size={14} /> Save</Button>
          <Button variant="secondary" disabled={status !== "custom"} onClick={() => void clear()}>Clear</Button>
        </div>
        <div className="mt-3 flex items-center gap-3">
          <Button size="sm" variant="secondary" disabled={status === "none" || testing} onClick={() => void test()}>{testing ? "Testing…" : "Test key"}</Button>
          {testResult && <span className={testResult.ok ? "text-sm text-prio-low" : "text-sm text-danger"}>{testResult.text}</span>}
        </div>
      </Row>
      <Row label="Model" hint={`Leave blank for ${DEFAULT_MODEL}.`}>
        <Input
          value={model === DEFAULT_MODEL ? "" : model}
          placeholder={DEFAULT_MODEL}
          onChange={(e) => setModelText(e.target.value || DEFAULT_MODEL)}
          onBlur={() => setModel(model)}
          className="font-mono"
        />
      </Row>
    </SettingsSection>
  );
}

export function PrivacySection() {
  const { requireHello, setRequireHello, lock, unlocked } = usePrivacy();
  return (
    <SettingsSection id="privacy" title="Privacy">
      <Row label="Lock Self with Windows Hello" hint="Asks for your PIN, face or fingerprint before showing the Self journal. Re-locks after 5 minutes in the background.">
        <div className="flex items-center gap-3">
          <label className="flex cursor-pointer items-center gap-2 text-sm">
            <input type="checkbox" className="h-4 w-4 accent-navy" checked={requireHello} onChange={(e) => setRequireHello(e.target.checked)} />
            Required
          </label>
          {requireHello && unlocked && <Button size="sm" variant="secondary" onClick={lock}><Fingerprint size={14} /> Lock now</Button>}
        </div>
      </Row>
    </SettingsSection>
  );
}
