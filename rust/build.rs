// Build script for the LiteParse JNI binding.
//
// Mirrors `crates/liteparse-napi/build.rs` from the upstream liteparse repo:
// PDFium is loaded at runtime via `libloading` (it is NOT linked), and the
// `liteparse-pdfium-sys` crate locates `pdfium-sys`'s own `self_dir()` probe to
// find `libpdfium` next to the loaded native library. We set an rpath so that,
// as a fallback, the dynamic loader also resolves a bare/`@rpath` pdfium name
// next to our `.so`/`.dylib`. On Windows this is unnecessary because the
// `self_dir()` mechanism uses an absolute path with `LoadLibrary`.

use std::env;

fn main() {
    let target_os = env::var("CARGO_CFG_TARGET_OS").unwrap_or_default();

    match target_os.as_str() {
        // @loader_path = directory containing the loaded .dylib (our JNI lib).
        "macos" => {
            println!("cargo:rustc-link-arg=-Wl,-rpath,@loader_path");
        }
        // $ORIGIN = directory containing the loaded .so (our JNI lib).
        "linux" => {
            println!("cargo:rustc-link-arg=-Wl,-rpath,$ORIGIN");
        }
        // Windows: dependent DLLs are resolved by self_dir()/LoadLibrary.
        _ => {}
    }

    // For local dev builds, also add the build-time pdfium directory so the
    // binary works without manually copying libpdfium next to it. This path is
    // exported by liteparse-pdfium-sys (which we depend on directly).
    if let Ok(lib_path) = env::var("DEP_PDFIUM_LIB_PATH") {
        if target_os == "macos" || target_os == "linux" {
            println!("cargo:rustc-link-arg=-Wl,-rpath,{lib_path}");
        }
        // Re-export for downstream tooling / CI staging scripts.
        println!("cargo:rustc-env=PDFIUM_LIB_DIR={lib_path}");
    }
}
