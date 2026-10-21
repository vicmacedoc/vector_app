//! Windows Hello gate for sensitive screens (Self journal), like the phone's biometric prompt.

use serde::Serialize;

#[derive(Serialize)]
#[serde(rename_all = "lowercase")]
pub enum HelloResult {
    Verified,
    Cancelled,
    /// No Hello PIN/biometric is set up on this PC; the caller decides whether to let the user in.
    Unavailable,
}

#[cfg(windows)]
fn verify_blocking(hwnd: isize, reason: &str) -> Result<HelloResult, String> {
    use windows::core::{factory, HSTRING};
    use windows::Security::Credentials::UI::{
        UserConsentVerificationResult, UserConsentVerifier, UserConsentVerifierAvailability,
    };
    use windows::Win32::Foundation::HWND;
    use windows::Win32::System::WinRT::IUserConsentVerifierInterop;
    use windows_future::IAsyncOperation;

    let availability = UserConsentVerifier::CheckAvailabilityAsync()
        .and_then(|op| op.join())
        .map_err(|e| e.to_string())?;
    if availability != UserConsentVerifierAvailability::Available {
        return Ok(HelloResult::Unavailable);
    }
    let interop = factory::<UserConsentVerifier, IUserConsentVerifierInterop>().map_err(|e| e.to_string())?;
    let op: IAsyncOperation<UserConsentVerificationResult> =
        unsafe { interop.RequestVerificationForWindowAsync(HWND(hwnd as _), &HSTRING::from(reason)) }
            .map_err(|e| e.to_string())?;
    let result = op.join().map_err(|e| e.to_string())?;
    Ok(match result {
        UserConsentVerificationResult::Verified => HelloResult::Verified,
        UserConsentVerificationResult::DeviceNotPresent
        | UserConsentVerificationResult::NotConfiguredForUser
        | UserConsentVerificationResult::DisabledByPolicy => HelloResult::Unavailable,
        _ => HelloResult::Cancelled,
    })
}

#[cfg(windows)]
#[tauri::command]
pub async fn hello_verify(window: tauri::WebviewWindow, reason: String) -> Result<HelloResult, String> {
    let hwnd = window.hwnd().map_err(|e| e.to_string())?.0 as isize;
    tauri::async_runtime::spawn_blocking(move || verify_blocking(hwnd, &reason))
        .await
        .map_err(|e| e.to_string())?
}

#[cfg(not(windows))]
#[tauri::command]
pub async fn hello_verify(_reason: String) -> Result<HelloResult, String> {
    Ok(HelloResult::Unavailable)
}
