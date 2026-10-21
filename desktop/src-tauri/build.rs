use std::path::Path;

/// Build-time Gemini key fallback: `GEMINI_API_KEY` from the environment, else the phone's
/// `local.properties` at the repo root, so one key serves both apps in local builds.
fn build_gemini_key() -> String {
    println!("cargo:rerun-if-env-changed=GEMINI_API_KEY");
    if let Ok(k) = std::env::var("GEMINI_API_KEY") {
        if !k.trim().is_empty() {
            return k.trim().to_string();
        }
    }
    let props = Path::new(env!("CARGO_MANIFEST_DIR")).join("../../local.properties");
    println!("cargo:rerun-if-changed={}", props.display());
    std::fs::read_to_string(props)
        .ok()
        .and_then(|text| {
            text.lines()
                .filter_map(|l| l.split_once('='))
                .find(|(k, _)| k.trim() == "GEMINI_API_KEY")
                .map(|(_, v)| v.trim().to_string())
        })
        .unwrap_or_default()
}

fn main() {
    println!("cargo:rustc-env=VECTOR_BUILD_GEMINI_KEY={}", build_gemini_key());
    tauri_build::build()
}
