//! Local JSON store, asset cache and binary Drive transfers (kept in Rust so file bytes never cross IPC).

use serde::Serialize;
use std::path::{Path, PathBuf};
use tauri::{AppHandle, Manager};
use tauri_plugin_http::reqwest;
use tauri_plugin_opener::OpenerExt;

const DRIVE_UPLOAD: &str = "https://www.googleapis.com/upload/drive/v3/files";
const DRIVE_FILES: &str = "https://www.googleapis.com/drive/v3/files";

fn sanitize(name: &str) -> String {
    let cleaned: String = name
        .chars()
        .map(|c| if "\\/:*?\"<>|".contains(c) || c.is_control() { '_' } else { c })
        .collect();
    let trimmed = cleaned.trim().trim_matches('.').to_string();
    let limited: String = trimmed.chars().take(200).collect();
    if limited.is_empty() { "file".into() } else { limited }
}

fn data_dir(app: &AppHandle) -> Result<PathBuf, String> {
    let dir = app.path().app_data_dir().map_err(|e| e.to_string())?;
    std::fs::create_dir_all(&dir).map_err(|e| e.to_string())?;
    Ok(dir)
}

fn cache_path(app: &AppHandle, asset_id: &str, file_name: &str) -> Result<PathBuf, String> {
    Ok(data_dir(app)?.join("cache").join(sanitize(asset_id)).join(sanitize(file_name)))
}

fn store_path(app: &AppHandle, name: &str) -> Result<PathBuf, String> {
    if name.is_empty() || name.contains(['/', '\\']) || name.contains("..") {
        return Err("invalid store name".into());
    }
    Ok(data_dir(app)?.join(name))
}

async fn write_atomic(path: &Path, bytes: &[u8]) -> Result<(), String> {
    if let Some(parent) = path.parent() {
        tokio::fs::create_dir_all(parent).await.map_err(|e| e.to_string())?;
    }
    let tmp = path.with_extension("tmp");
    tokio::fs::write(&tmp, bytes).await.map_err(|e| e.to_string())?;
    tokio::fs::rename(&tmp, path).await.map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn store_read(app: AppHandle, name: String) -> Result<Option<String>, String> {
    let path = store_path(&app, &name)?;
    match tokio::fs::read_to_string(&path).await {
        Ok(s) => Ok(Some(s)),
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => Ok(None),
        Err(e) => Err(e.to_string()),
    }
}

#[tauri::command]
pub async fn store_write(app: AppHandle, name: String, contents: String) -> Result<(), String> {
    write_atomic(&store_path(&app, &name)?, contents.as_bytes()).await
}

#[tauri::command]
pub async fn store_delete(app: AppHandle, name: String) -> Result<(), String> {
    match tokio::fs::remove_file(store_path(&app, &name)?).await {
        Ok(()) => Ok(()),
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => Ok(()),
        Err(e) => Err(e.to_string()),
    }
}

/// Path of a cached asset file, if it was downloaded or added from this computer.
#[tauri::command]
pub fn cache_lookup(app: AppHandle, asset_id: String, file_name: String) -> Result<Option<String>, String> {
    let path = cache_path(&app, &asset_id, &file_name)?;
    Ok(path.exists().then(|| path.to_string_lossy().into_owned()))
}

#[tauri::command]
pub async fn cache_from_local(
    app: AppHandle,
    source: String,
    asset_id: String,
    file_name: String,
) -> Result<String, String> {
    let dest = cache_path(&app, &asset_id, &file_name)?;
    if let Some(parent) = dest.parent() {
        tokio::fs::create_dir_all(parent).await.map_err(|e| e.to_string())?;
    }
    tokio::fs::copy(&source, &dest).await.map_err(|e| e.to_string())?;
    Ok(dest.to_string_lossy().into_owned())
}

/// Writes bytes produced in the webview (recordings, webcam shots) into the asset cache.
#[tauri::command]
pub async fn cache_write_base64(
    app: AppHandle,
    asset_id: String,
    file_name: String,
    data_base64: String,
) -> Result<String, String> {
    use base64::Engine;
    let bytes = base64::engine::general_purpose::STANDARD
        .decode(data_base64.as_bytes())
        .map_err(|e| e.to_string())?;
    let dest = cache_path(&app, &asset_id, &file_name)?;
    write_atomic(&dest, &bytes).await?;
    Ok(dest.to_string_lossy().into_owned())
}

/// Base64 of a local file for inline AI requests; errors above `max_bytes`.
#[tauri::command]
pub async fn read_file_base64(path: String, max_bytes: u64) -> Result<String, String> {
    use base64::Engine;
    let meta = tokio::fs::metadata(&path).await.map_err(|e| e.to_string())?;
    if meta.len() > max_bytes {
        return Err(format!("file is larger than {} MB", max_bytes / 1_000_000));
    }
    let bytes = tokio::fs::read(&path).await.map_err(|e| e.to_string())?;
    Ok(base64::engine::general_purpose::STANDARD.encode(bytes))
}

#[tauri::command]
pub async fn cache_remove(app: AppHandle, asset_id: String) -> Result<(), String> {
    let dir = data_dir(&app)?.join("cache").join(sanitize(&asset_id));
    match tokio::fs::remove_dir_all(dir).await {
        Ok(()) => Ok(()),
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => Ok(()),
        Err(e) => Err(e.to_string()),
    }
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct LocalFileInfo {
    name: String,
    size: u64,
    mime_type: String,
}

fn mime_for(name: &str) -> &'static str {
    let ext = name.rsplit('.').next().unwrap_or("").to_ascii_lowercase();
    match ext.as_str() {
        "jpg" | "jpeg" => "image/jpeg",
        "png" => "image/png",
        "gif" => "image/gif",
        "webp" => "image/webp",
        "heic" => "image/heic",
        "svg" => "image/svg+xml",
        "pdf" => "application/pdf",
        "txt" => "text/plain",
        "md" | "markdown" => "text/markdown",
        "json" => "application/json",
        "csv" => "text/csv",
        "html" | "htm" => "text/html",
        "mp3" => "audio/mpeg",
        "m4a" => "audio/mp4",
        "wav" => "audio/wav",
        "ogg" | "oga" => "audio/ogg",
        "flac" => "audio/flac",
        "mp4" | "m4v" => "video/mp4",
        "mov" => "video/quicktime",
        "webm" => "video/webm",
        "mkv" => "video/x-matroska",
        "doc" => "application/msword",
        "docx" => "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xlsx" => "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "pptx" => "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "epub" => "application/epub+zip",
        "zip" => "application/zip",
        _ => "application/octet-stream",
    }
}

#[tauri::command]
pub async fn file_info(path: String) -> Result<LocalFileInfo, String> {
    let meta = tokio::fs::metadata(&path).await.map_err(|e| e.to_string())?;
    if !meta.is_file() {
        return Err("not a file".into());
    }
    let name = Path::new(&path).file_name().map(|n| n.to_string_lossy().into_owned()).unwrap_or_else(|| "file".into());
    let mime_type = mime_for(&name).to_string();
    Ok(LocalFileInfo { name, size: meta.len(), mime_type })
}

/// Text of a small local file (None when larger than `max_bytes` or not UTF-8).
#[tauri::command]
pub async fn read_text_file(path: String, max_bytes: u64) -> Result<Option<String>, String> {
    let meta = tokio::fs::metadata(&path).await.map_err(|e| e.to_string())?;
    if meta.len() > max_bytes {
        return Ok(None);
    }
    let bytes = tokio::fs::read(&path).await.map_err(|e| e.to_string())?;
    Ok(String::from_utf8(bytes).ok())
}

/// Writes an export the user picked a destination for in the save dialog.
#[tauri::command]
pub async fn write_text_file(path: String, contents: String) -> Result<(), String> {
    tokio::fs::write(&path, contents).await.map_err(|e| e.to_string())
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct UploadedFile {
    id: String,
    size: u64,
}

/// Multipart upload of a local file into a Drive folder.
#[tauri::command]
pub async fn drive_upload(
    token: String,
    parent_id: String,
    name: String,
    mime_type: String,
    path: String,
) -> Result<UploadedFile, String> {
    let bytes = tokio::fs::read(&path).await.map_err(|e| e.to_string())?;
    let size = bytes.len() as u64;
    let boundary = format!("vector_{}", rand_boundary());
    let metadata = serde_json::json!({ "name": name, "parents": [parent_id] }).to_string();
    let mut body = Vec::with_capacity(bytes.len() + metadata.len() + 256);
    body.extend_from_slice(format!("--{boundary}\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n{metadata}\r\n").as_bytes());
    body.extend_from_slice(format!("--{boundary}\r\nContent-Type: {mime_type}\r\n\r\n").as_bytes());
    body.extend_from_slice(&bytes);
    body.extend_from_slice(format!("\r\n--{boundary}--").as_bytes());

    let response = reqwest::Client::new()
        .post(format!("{DRIVE_UPLOAD}?uploadType=multipart&fields=id"))
        .bearer_auth(&token)
        .header("Content-Type", format!("multipart/related; boundary={boundary}"))
        .body(body)
        .send()
        .await
        .map_err(|e| e.to_string())?;
    let status = response.status();
    let text = response.text().await.map_err(|e| e.to_string())?;
    if !status.is_success() {
        return Err(format!("upload failed ({status}): {text}"));
    }
    let json: serde_json::Value = serde_json::from_str(&text).map_err(|e| e.to_string())?;
    let id = json["id"].as_str().ok_or("upload returned no id")?.to_string();
    Ok(UploadedFile { id, size })
}

fn rand_boundary() -> String {
    use rand::Rng;
    let mut rng = rand::thread_rng();
    (0..24).map(|_| char::from(b'a' + rng.gen_range(0..26))).collect()
}

/// Downloads a Drive file into the asset cache and returns its local path.
#[tauri::command]
pub async fn drive_download(
    app: AppHandle,
    token: String,
    file_id: String,
    asset_id: String,
    file_name: String,
) -> Result<String, String> {
    let dest = cache_path(&app, &asset_id, &file_name)?;
    let response = reqwest::Client::new()
        .get(format!("{DRIVE_FILES}/{file_id}?alt=media"))
        .bearer_auth(&token)
        .send()
        .await
        .map_err(|e| e.to_string())?;
    let status = response.status();
    if !status.is_success() {
        let text = response.text().await.unwrap_or_default();
        return Err(format!("download failed ({status}): {text}"));
    }
    let bytes = response.bytes().await.map_err(|e| e.to_string())?;
    write_atomic(&dest, &bytes).await?;
    Ok(dest.to_string_lossy().into_owned())
}

#[tauri::command]
pub fn open_path(app: AppHandle, path: String) -> Result<(), String> {
    app.opener().open_path(path, None::<&str>).map_err(|e| e.to_string())
}
