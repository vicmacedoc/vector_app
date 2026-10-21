//! Google sign-in for an installed app: system browser + loopback redirect + PKCE.
//! The refresh token lives in Windows Credential Manager; access tokens only in memory.

use base64::engine::general_purpose::URL_SAFE_NO_PAD;
use base64::Engine;
use rand::RngCore;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::sync::Mutex;
use std::time::{Duration, SystemTime, UNIX_EPOCH};
use tauri::{AppHandle, State};
use tauri_plugin_http::reqwest;
use tauri_plugin_opener::OpenerExt;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::TcpListener;

const AUTH_URL: &str = "https://accounts.google.com/o/oauth2/v2/auth";
const TOKEN_URL: &str = "https://oauth2.googleapis.com/token";
const SCOPE: &str = "https://www.googleapis.com/auth/drive https://www.googleapis.com/auth/calendar.readonly";
const KEYRING_SERVICE: &str = "vector-desktop";
const KEYRING_USER: &str = "google-refresh-token";
const SIGN_IN_TIMEOUT: Duration = Duration::from_secs(300);

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AccessToken {
    access_token: String,
    expires_at_ms: u64,
}

#[derive(Default)]
pub struct TokenCache(Mutex<Option<AccessToken>>);

#[derive(Deserialize)]
struct TokenResponse {
    access_token: String,
    expires_in: Option<u64>,
    refresh_token: Option<String>,
}

fn now_ms() -> u64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_millis() as u64).unwrap_or(0)
}

fn random_token(bytes: usize) -> String {
    let mut buf = vec![0u8; bytes];
    rand::thread_rng().fill_bytes(&mut buf);
    URL_SAFE_NO_PAD.encode(buf)
}

fn keyring_entry() -> Result<keyring::Entry, String> {
    keyring::Entry::new(KEYRING_SERVICE, KEYRING_USER).map_err(|e| e.to_string())
}

fn form_body(pairs: &[(&str, &str)]) -> String {
    let mut s = url::form_urlencoded::Serializer::new(String::new());
    for (k, v) in pairs {
        s.append_pair(k, v);
    }
    s.finish()
}

async fn post_token(pairs: &[(&str, &str)]) -> Result<TokenResponse, String> {
    let response = reqwest::Client::new()
        .post(TOKEN_URL)
        .header("Content-Type", "application/x-www-form-urlencoded")
        .body(form_body(pairs))
        .send()
        .await
        .map_err(|e| format!("token request failed: {e}"))?;
    let status = response.status();
    let text = response.text().await.map_err(|e| e.to_string())?;
    if !status.is_success() {
        if text.contains("invalid_grant") {
            return Err("signed_out".into());
        }
        return Err(format!("token endpoint {status}: {text}"));
    }
    serde_json::from_str(&text).map_err(|e| format!("bad token response: {e}"))
}

fn remember(cache: &TokenCache, token: &TokenResponse) -> AccessToken {
    let access = AccessToken {
        access_token: token.access_token.clone(),
        expires_at_ms: now_ms() + token.expires_in.unwrap_or(3600) * 1000,
    };
    *cache.0.lock().unwrap() = Some(access.clone());
    access
}

/// Reads `GET /?code=...&state=...` from the browser redirect and answers with a small page.
async fn wait_for_code(listener: TcpListener, expected_state: &str) -> Result<String, String> {
    loop {
        let (mut stream, _) = listener.accept().await.map_err(|e| e.to_string())?;
        let mut buf = vec![0u8; 8192];
        let n = stream.read(&mut buf).await.map_err(|e| e.to_string())?;
        let request = String::from_utf8_lossy(&buf[..n]);
        let path = request.split_whitespace().nth(1).unwrap_or("/").to_string();
        let parsed = url::Url::parse(&format!("http://127.0.0.1{path}")).map_err(|e| e.to_string())?;
        let mut code = None;
        let mut state = None;
        let mut error = None;
        for (k, v) in parsed.query_pairs() {
            match k.as_ref() {
                "code" => code = Some(v.to_string()),
                "state" => state = Some(v.to_string()),
                "error" => error = Some(v.to_string()),
                _ => {}
            }
        }
        if code.is_none() && error.is_none() {
            // Favicon or other stray request.
            let _ = stream.write_all(b"HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n").await;
            continue;
        }
        let ok = error.is_none() && state.as_deref() == Some(expected_state);
        let body = if ok {
            "<html><body style=\"font-family:system-ui;background:#001F3F;color:#fff;display:flex;align-items:center;justify-content:center;height:100vh;margin:0\"><div><h2>Vector Brain is signed in.</h2><p>You can close this tab.</p></div></body></html>"
        } else {
            "<html><body style=\"font-family:system-ui\"><h2>Sign-in failed.</h2><p>Return to Vector Brain and try again.</p></body></html>"
        };
        let response = format!(
            "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
            body.len(),
            body
        );
        let _ = stream.write_all(response.as_bytes()).await;
        let _ = stream.flush().await;
        if let Some(e) = error {
            return Err(format!("Google returned: {e}"));
        }
        if !ok {
            return Err("sign-in state mismatch".into());
        }
        return code.ok_or_else(|| "no code".into());
    }
}

#[tauri::command]
pub async fn oauth_sign_in(
    app: AppHandle,
    cache: State<'_, TokenCache>,
    client_id: String,
    client_secret: String,
) -> Result<AccessToken, String> {
    if client_id.trim().is_empty() {
        return Err("VITE_GOOGLE_CLIENT_ID is not set (see desktop/README.md)".into());
    }
    let listener = TcpListener::bind("127.0.0.1:0").await.map_err(|e| e.to_string())?;
    let port = listener.local_addr().map_err(|e| e.to_string())?.port();
    let redirect_uri = format!("http://127.0.0.1:{port}");
    let verifier = random_token(48);
    let challenge = URL_SAFE_NO_PAD.encode(Sha256::digest(verifier.as_bytes()));
    let state = random_token(16);

    let auth_url = url::Url::parse_with_params(
        AUTH_URL,
        &[
            ("client_id", client_id.as_str()),
            ("redirect_uri", redirect_uri.as_str()),
            ("response_type", "code"),
            ("scope", SCOPE),
            ("code_challenge", challenge.as_str()),
            ("code_challenge_method", "S256"),
            ("access_type", "offline"),
            ("prompt", "consent"),
            ("state", state.as_str()),
        ],
    )
    .map_err(|e| e.to_string())?;
    app.opener().open_url(auth_url.as_str(), None::<&str>).map_err(|e| e.to_string())?;

    let code = tokio::time::timeout(SIGN_IN_TIMEOUT, wait_for_code(listener, &state))
        .await
        .map_err(|_| "sign-in timed out".to_string())??;

    let token = post_token(&[
        ("code", code.as_str()),
        ("client_id", client_id.as_str()),
        ("client_secret", client_secret.as_str()),
        ("redirect_uri", redirect_uri.as_str()),
        ("grant_type", "authorization_code"),
        ("code_verifier", verifier.as_str()),
    ])
    .await?;
    let refresh = token
        .refresh_token
        .clone()
        .ok_or_else(|| "Google did not return a refresh token".to_string())?;
    keyring_entry()?.set_password(&refresh).map_err(|e| e.to_string())?;
    Ok(remember(&cache, &token))
}

/// Cached access token, refreshed a minute before expiry (or when `force` is set after a 401).
#[tauri::command]
pub async fn oauth_access_token(
    cache: State<'_, TokenCache>,
    client_id: String,
    client_secret: String,
    force: bool,
) -> Result<AccessToken, String> {
    if !force {
        if let Some(t) = cache.0.lock().unwrap().clone() {
            if t.expires_at_ms > now_ms() + 60_000 {
                return Ok(t);
            }
        }
    }
    let refresh = match keyring_entry()?.get_password() {
        Ok(r) => r,
        Err(keyring::Error::NoEntry) => return Err("signed_out".into()),
        Err(e) => return Err(e.to_string()),
    };
    let token = post_token(&[
        ("refresh_token", refresh.as_str()),
        ("client_id", client_id.as_str()),
        ("client_secret", client_secret.as_str()),
        ("grant_type", "refresh_token"),
    ])
    .await;
    match token {
        Ok(t) => Ok(remember(&cache, &t)),
        Err(e) if e == "signed_out" => {
            let _ = keyring_entry().map(|k| k.delete_credential());
            Err(e)
        }
        Err(e) => Err(e),
    }
}

#[tauri::command]
pub fn oauth_signed_in() -> bool {
    keyring_entry().and_then(|k| k.get_password().map_err(|e| e.to_string())).is_ok()
}

#[tauri::command]
pub fn oauth_sign_out(cache: State<'_, TokenCache>) -> Result<(), String> {
    *cache.0.lock().unwrap() = None;
    match keyring_entry()?.delete_credential() {
        Ok(()) | Err(keyring::Error::NoEntry) => Ok(()),
        Err(e) => Err(e.to_string()),
    }
}
