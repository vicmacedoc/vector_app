//! Gemini proxy: the API key lives in Windows Credential Manager (or is baked in at build time)
//! and never reaches the webview. The frontend sends a `generateContent` body and gets the response.

use serde::Serialize;
use std::time::Duration;
use tauri_plugin_http::reqwest;

const KEYRING_SERVICE: &str = "vector-desktop";
const KEYRING_USER: &str = "gemini-api-key";
const BUILD_KEY: &str = env!("VECTOR_BUILD_GEMINI_KEY");
const ENDPOINT: &str = "https://generativelanguage.googleapis.com/v1beta/models";
const TIMEOUT: Duration = Duration::from_secs(120);
pub const MISSING_KEY: &str = "Gemini API key missing. Add it in Settings → AI, or set GEMINI_API_KEY in local.properties.";

#[derive(Serialize)]
#[serde(rename_all = "lowercase")]
pub enum KeySource {
    Custom,
    Build,
    None,
}

fn entry() -> Result<keyring::Entry, String> {
    keyring::Entry::new(KEYRING_SERVICE, KEYRING_USER).map_err(|e| e.to_string())
}

fn custom_key() -> Option<String> {
    entry().ok()?.get_password().ok().filter(|k| !k.trim().is_empty())
}

fn resolve() -> Option<(String, KeySource)> {
    if let Some(k) = custom_key() {
        return Some((k, KeySource::Custom));
    }
    if !BUILD_KEY.trim().is_empty() {
        return Some((BUILD_KEY.trim().to_string(), KeySource::Build));
    }
    None
}

#[tauri::command]
pub fn gemini_status() -> KeySource {
    resolve().map(|(_, s)| s).unwrap_or(KeySource::None)
}

#[tauri::command]
pub fn gemini_key_set(key: String) -> Result<(), String> {
    let key = key.trim();
    if key.is_empty() {
        return Err("Key is empty".into());
    }
    entry()?.set_password(key).map_err(|e| e.to_string())
}

#[tauri::command]
pub fn gemini_key_clear() -> Result<(), String> {
    match entry()?.delete_credential() {
        Ok(()) | Err(keyring::Error::NoEntry) => Ok(()),
        Err(e) => Err(e.to_string()),
    }
}

/// Google's `{"error":{"message":...}}` when present, else the raw status.
fn api_error(status: reqwest::StatusCode, text: &str) -> String {
    serde_json::from_str::<serde_json::Value>(text)
        .ok()
        .and_then(|v| v["error"]["message"].as_str().map(str::to_string))
        .unwrap_or_else(|| format!("Gemini request failed (HTTP {status})"))
}

#[tauri::command]
pub async fn gemini_generate(model: String, body: serde_json::Value) -> Result<serde_json::Value, String> {
    let (key, _) = resolve().ok_or_else(|| MISSING_KEY.to_string())?;
    let model = model.trim();
    if model.is_empty() || !model.chars().all(|c| c.is_ascii_alphanumeric() || "-._".contains(c)) {
        return Err(format!("Invalid model name: {model}"));
    }
    let response = reqwest::Client::new()
        .post(format!("{ENDPOINT}/{model}:generateContent"))
        .header("Content-Type", "application/json; charset=utf-8")
        .header("x-goog-api-key", key)
        .timeout(TIMEOUT)
        .body(body.to_string())
        .send()
        .await
        .map_err(|e| format!("Gemini request failed: {e}"))?;
    let status = response.status();
    let text = response.text().await.map_err(|e| e.to_string())?;
    if !status.is_success() {
        return Err(api_error(status, &text));
    }
    serde_json::from_str(&text).map_err(|e| format!("Bad Gemini response: {e}"))
}
