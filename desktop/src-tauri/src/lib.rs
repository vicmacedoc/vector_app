mod files;
mod gemini;
mod hello;
mod oauth;

use tauri::{AppHandle, Emitter, Manager, WindowEvent};
use tauri_plugin_global_shortcut::{Code, GlobalShortcutExt, Modifiers, Shortcut, ShortcutState};

fn show_main(app: &AppHandle) {
    if let Some(w) = app.get_webview_window("main") {
        let _ = w.unminimize();
        let _ = w.show();
        let _ = w.set_focus();
    }
}

fn show_capture(app: &AppHandle) {
    if let Some(w) = app.get_webview_window("capture") {
        let _ = w.center();
        let _ = w.show();
        let _ = w.set_focus();
        let _ = w.emit("capture-opened", ());
    }
}

#[tauri::command]
fn open_capture(app: AppHandle) {
    show_capture(&app);
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let capture_shortcut = Shortcut::new(Some(Modifiers::CONTROL | Modifiers::SHIFT), Code::Space);

    tauri::Builder::default()
        .plugin(tauri_plugin_single_instance::init(|app, _args, _cwd| show_main(app)))
        .plugin(tauri_plugin_http::init())
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_opener::init())
        .plugin(
            tauri_plugin_global_shortcut::Builder::new()
                .with_handler(move |app, shortcut, event| {
                    if shortcut == &capture_shortcut && event.state() == ShortcutState::Pressed {
                        show_capture(app);
                    }
                })
                .build(),
        )
        .manage(oauth::TokenCache::default())
        .setup(move |app| {
            // Another program may own the shortcut; the app still works without it.
            if let Err(e) = app.global_shortcut().register(capture_shortcut) {
                eprintln!("quick-capture shortcut unavailable: {e}");
            }
            Ok(())
        })
        .on_window_event(|window, event| {
            if window.label() == "capture" {
                if let WindowEvent::CloseRequested { api, .. } = event {
                    api.prevent_close();
                    let _ = window.hide();
                }
            } else if window.label() == "main" {
                if let WindowEvent::Destroyed = event {
                    window.app_handle().exit(0);
                }
            }
        })
        .invoke_handler(tauri::generate_handler![
            open_capture,
            oauth::oauth_sign_in,
            oauth::oauth_access_token,
            oauth::oauth_signed_in,
            oauth::oauth_sign_out,
            files::store_read,
            files::store_write,
            files::store_delete,
            files::cache_lookup,
            files::cache_from_local,
            files::cache_remove,
            files::cache_write_base64,
            files::read_file_base64,
            files::file_info,
            files::read_text_file,
            files::write_text_file,
            files::drive_upload,
            files::drive_download,
            files::open_path,
            gemini::gemini_status,
            gemini::gemini_key_set,
            gemini::gemini_key_clear,
            gemini::gemini_generate,
            hello::hello_verify,
        ])
        .run(tauri::generate_context!())
        .expect("error while running Vector Brain");
}
