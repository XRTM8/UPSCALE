fn main() {
    // The Android cdylib does not need a Windows DLL export table.
    if std::env::var("CARGO_CFG_TARGET_OS").as_deref() == Ok("windows")
        && std::env::var("CARGO_CFG_TARGET_ENV").as_deref() == Ok("gnu")
    {
        println!("cargo:rustc-link-arg-cdylib=-Wl,--exclude-all-symbols");
    }
    tauri_build::build()
}
