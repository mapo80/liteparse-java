//! JNI native bindings for LiteParse.
//!
//! This crate is the Java counterpart of `liteparse-napi` (Node) and
//! `liteparse-python` (PyO3): a `cdylib` that wraps the core `liteparse` crate
//! and exposes it to the JVM. The complex data (config, parse results) is
//! marshalled across the boundary as **JSON strings** (the core types are
//! already `serde`-serializable); binary screenshot data is returned as
//! `byte[]`. PDFium is loaded at runtime by `liteparse` from a copy bundled
//! next to this library — see `build.rs`.
//!
//! Native methods correspond to `io.liteparse.NativeBindings`.

use jni::objects::{JByteArray, JClass, JIntArray, JObject, JObjectArray, JString, JValue};
use jni::sys::{jlong, jobjectArray, jstring};
use jni::JNIEnv;

use liteparse::config::{LiteParseConfig, OutputFormat};
use liteparse::parser::LiteParse;
use liteparse::search::{search_items, SearchOptions};
use liteparse::types::{ParsedPage, PdfInput, TextItem};

use serde::{Deserialize, Serialize};
use tokio::runtime::Runtime;

const EXCEPTION_CLASS: &str = "io/liteparse/LiteParseException";

// ---------------------------------------------------------------------------
// Handle held by the Java `LiteParse` object as a `long`.
// ---------------------------------------------------------------------------

struct ParserHandle {
    inner: LiteParse,
    runtime: Runtime,
    config: LiteParseConfig,
}

// ---------------------------------------------------------------------------
// JSON DTOs — camelCase wire format, matching the Java POJOs exactly. These
// mirror the `Js*` types in `crates/liteparse-napi/src/types.rs`.
// ---------------------------------------------------------------------------

#[derive(Debug, Clone, Default, Deserialize)]
#[serde(rename_all = "camelCase", default)]
struct PartialConfig {
    ocr_language: Option<String>,
    ocr_enabled: Option<bool>,
    ocr_server_url: Option<String>,
    tessdata_path: Option<String>,
    max_pages: Option<u32>,
    target_pages: Option<String>,
    dpi: Option<f64>,
    output_format: Option<String>,
    preserve_very_small_text: Option<bool>,
    password: Option<String>,
    quiet: Option<bool>,
    num_workers: Option<u32>,
}

impl PartialConfig {
    fn apply_onto_default(self) -> LiteParseConfig {
        let mut cfg = LiteParseConfig::default();
        if let Some(v) = self.ocr_language {
            cfg.ocr_language = v;
        }
        if let Some(v) = self.ocr_enabled {
            cfg.ocr_enabled = v;
        }
        if self.ocr_server_url.is_some() {
            cfg.ocr_server_url = self.ocr_server_url;
        }
        if self.tessdata_path.is_some() {
            cfg.tessdata_path = self.tessdata_path;
        }
        if let Some(v) = self.max_pages {
            cfg.max_pages = v as usize;
        }
        if self.target_pages.is_some() {
            cfg.target_pages = self.target_pages;
        }
        if let Some(v) = self.dpi {
            cfg.dpi = v as f32;
        }
        if let Some(v) = self.output_format {
            cfg.output_format = match v.as_str() {
                "text" => OutputFormat::Text,
                _ => OutputFormat::Json,
            };
        }
        if let Some(v) = self.preserve_very_small_text {
            cfg.preserve_very_small_text = v;
        }
        if self.password.is_some() {
            cfg.password = self.password;
        }
        if let Some(v) = self.quiet {
            cfg.quiet = v;
        }
        if let Some(v) = self.num_workers {
            cfg.num_workers = v as usize;
        }
        cfg
    }
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct DtoConfig {
    ocr_language: String,
    ocr_enabled: bool,
    ocr_server_url: Option<String>,
    tessdata_path: Option<String>,
    max_pages: u32,
    target_pages: Option<String>,
    dpi: f64,
    output_format: String,
    preserve_very_small_text: bool,
    password: Option<String>,
    quiet: bool,
    num_workers: u32,
}

impl DtoConfig {
    fn from_core(cfg: &LiteParseConfig) -> Self {
        Self {
            ocr_language: cfg.ocr_language.clone(),
            ocr_enabled: cfg.ocr_enabled,
            ocr_server_url: cfg.ocr_server_url.clone(),
            tessdata_path: cfg.tessdata_path.clone(),
            max_pages: cfg.max_pages as u32,
            target_pages: cfg.target_pages.clone(),
            dpi: cfg.dpi as f64,
            output_format: match cfg.output_format {
                OutputFormat::Json => "json".to_string(),
                OutputFormat::Text => "text".to_string(),
            },
            preserve_very_small_text: cfg.preserve_very_small_text,
            password: cfg.password.clone(),
            quiet: cfg.quiet,
            num_workers: cfg.num_workers as u32,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DtoTextItem {
    text: String,
    x: f64,
    y: f64,
    width: f64,
    height: f64,
    #[serde(skip_serializing_if = "Option::is_none")]
    font_name: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    font_size: Option<f64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    font_weight: Option<i32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    font_flags: Option<i32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    confidence: Option<f64>,
}

impl DtoTextItem {
    fn from_core(item: &TextItem) -> Self {
        Self {
            text: item.text.clone(),
            x: item.x as f64,
            y: item.y as f64,
            width: item.width as f64,
            height: item.height as f64,
            font_name: item.font_name.clone(),
            font_size: item.font_size.map(|v| v as f64),
            font_weight: item.font_weight,
            font_flags: item.font_flags,
            confidence: item.confidence.map(|v| v as f64).or(Some(1.0)),
        }
    }

    fn to_core(&self) -> TextItem {
        TextItem {
            text: self.text.clone(),
            x: self.x as f32,
            y: self.y as f32,
            width: self.width as f32,
            height: self.height as f32,
            font_name: self.font_name.clone(),
            font_size: self.font_size.map(|v| v as f32),
            font_weight: self.font_weight,
            font_flags: self.font_flags,
            confidence: self.confidence.map(|v| v as f32),
            ..Default::default()
        }
    }
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct DtoParsedPage {
    page_num: u32,
    width: f64,
    height: f64,
    text: String,
    text_items: Vec<DtoTextItem>,
}

impl DtoParsedPage {
    fn from_core(page: &ParsedPage) -> Self {
        Self {
            page_num: page.page_number as u32,
            width: page.page_width as f64,
            height: page.page_height as f64,
            text: page.text.clone(),
            text_items: page.text_items.iter().map(DtoTextItem::from_core).collect(),
        }
    }
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct DtoParseResult {
    pages: Vec<DtoParsedPage>,
    text: String,
}

// ---------------------------------------------------------------------------
// Error helpers
// ---------------------------------------------------------------------------

/// Throw a `LiteParseException` into the JVM. Safe to call even if another
/// exception is pending (JNI no-ops the second throw).
fn throw(env: &mut JNIEnv, msg: impl AsRef<str>) {
    let _ = env.throw_new(EXCEPTION_CLASS, msg.as_ref());
}

unsafe fn handle_ref<'a>(handle: jlong) -> &'a ParserHandle {
    &*(handle as *const ParserHandle)
}

// ---------------------------------------------------------------------------
// JNI exports — class io.liteparse.NativeBindings
// ---------------------------------------------------------------------------

/// Create a new parser from a partial config JSON (camelCase). Returns an
/// opaque handle (pointer) as a `long`, or 0 if an exception was thrown.
#[no_mangle]
pub extern "system" fn Java_io_liteparse_NativeBindings_nativeNew<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    config_json: JString<'local>,
) -> jlong {
    let json: String = match env.get_string(&config_json) {
        Ok(s) => s.into(),
        Err(e) => {
            throw(&mut env, format!("failed to read config JSON: {e}"));
            return 0;
        }
    };

    let partial: PartialConfig = if json.trim().is_empty() {
        PartialConfig::default()
    } else {
        match serde_json::from_str(&json) {
            Ok(p) => p,
            Err(e) => {
                throw(&mut env, format!("invalid config JSON: {e}"));
                return 0;
            }
        }
    };

    let config = partial.apply_onto_default();

    let runtime = match Runtime::new() {
        Ok(rt) => rt,
        Err(e) => {
            throw(&mut env, format!("failed to create async runtime: {e}"));
            return 0;
        }
    };

    let inner = LiteParse::new(config.clone());
    let handle = Box::new(ParserHandle {
        inner,
        runtime,
        config,
    });
    Box::into_raw(handle) as jlong
}

/// Free the native parser handle.
#[no_mangle]
pub extern "system" fn Java_io_liteparse_NativeBindings_nativeClose<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
) {
    if handle != 0 {
        unsafe {
            drop(Box::from_raw(handle as *mut ParserHandle));
        }
    }
}

/// Return the resolved configuration as a camelCase JSON string.
#[no_mangle]
pub extern "system" fn Java_io_liteparse_NativeBindings_nativeResolvedConfig<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
) -> jstring {
    let parser = unsafe { handle_ref(handle) };
    let dto = DtoConfig::from_core(&parser.config);
    match serde_json::to_string(&dto) {
        Ok(s) => match env.new_string(s) {
            Ok(js) => js.into_raw(),
            Err(e) => {
                throw(&mut env, format!("failed to build config string: {e}"));
                std::ptr::null_mut()
            }
        },
        Err(e) => {
            throw(&mut env, format!("failed to serialize config: {e}"));
            std::ptr::null_mut()
        }
    }
}

fn parse_and_serialize(env: &mut JNIEnv, parser: &ParserHandle, input: PdfInput) -> Option<String> {
    let result = parser.runtime.block_on(parser.inner.parse_input(input));
    match result {
        Ok(r) => {
            let dto = DtoParseResult {
                pages: r.pages.iter().map(DtoParsedPage::from_core).collect(),
                text: r.text,
            };
            match serde_json::to_string(&dto) {
                Ok(s) => Some(s),
                Err(e) => {
                    throw(env, format!("failed to serialize parse result: {e}"));
                    None
                }
            }
        }
        Err(e) => {
            throw(env, format!("parse failed: {e}"));
            None
        }
    }
}

/// Parse a document from a file path. Returns a ParseResult JSON string.
#[no_mangle]
pub extern "system" fn Java_io_liteparse_NativeBindings_nativeParsePath<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    path: JString<'local>,
) -> jstring {
    let parser = unsafe { handle_ref(handle) };
    let path: String = match env.get_string(&path) {
        Ok(s) => s.into(),
        Err(e) => {
            throw(&mut env, format!("failed to read path: {e}"));
            return std::ptr::null_mut();
        }
    };

    match parse_and_serialize(&mut env, parser, PdfInput::Path(path)) {
        Some(s) => match env.new_string(s) {
            Ok(js) => js.into_raw(),
            Err(e) => {
                throw(&mut env, format!("failed to build result string: {e}"));
                std::ptr::null_mut()
            }
        },
        None => std::ptr::null_mut(),
    }
}

/// Parse a document from raw bytes. Returns a ParseResult JSON string.
#[no_mangle]
pub extern "system" fn Java_io_liteparse_NativeBindings_nativeParseBytes<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    data: JByteArray<'local>,
) -> jstring {
    let parser = unsafe { handle_ref(handle) };
    let bytes = match env.convert_byte_array(&data) {
        Ok(b) => b,
        Err(e) => {
            throw(&mut env, format!("failed to read byte input: {e}"));
            return std::ptr::null_mut();
        }
    };

    match parse_and_serialize(&mut env, parser, PdfInput::Bytes(bytes)) {
        Some(s) => match env.new_string(s) {
            Ok(js) => js.into_raw(),
            Err(e) => {
                throw(&mut env, format!("failed to build result string: {e}"));
                std::ptr::null_mut()
            }
        },
        None => std::ptr::null_mut(),
    }
}

/// Take screenshots of pages. Returns an array of `io.liteparse.ScreenshotResult`.
#[no_mangle]
pub extern "system" fn Java_io_liteparse_NativeBindings_nativeScreenshot<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    path: JString<'local>,
    pages: JIntArray<'local>,
) -> jobjectArray {
    let parser = unsafe { handle_ref(handle) };

    let path: String = match env.get_string(&path) {
        Ok(s) => s.into(),
        Err(e) => {
            throw(&mut env, format!("failed to read path: {e}"));
            return std::ptr::null_mut();
        }
    };

    // An empty / null page array means "all pages".
    let page_numbers: Option<Vec<u32>> = if pages.is_null() {
        None
    } else {
        match env.get_array_length(&pages) {
            Ok(0) => None,
            Ok(len) => {
                let mut buf = vec![0i32; len as usize];
                if let Err(e) = env.get_int_array_region(&pages, 0, &mut buf) {
                    throw(&mut env, format!("failed to read page numbers: {e}"));
                    return std::ptr::null_mut();
                }
                Some(buf.into_iter().map(|v| v as u32).collect())
            }
            Err(e) => {
                throw(&mut env, format!("failed to read page array: {e}"));
                return std::ptr::null_mut();
            }
        }
    };

    let results = match parser
        .runtime
        .block_on(parser.inner.screenshot(&path, page_numbers))
    {
        Ok(r) => r,
        Err(e) => {
            throw(&mut env, format!("screenshot failed: {e}"));
            return std::ptr::null_mut();
        }
    };

    // Build ScreenshotResult[] in JNI.
    let class = match env.find_class("io/liteparse/ScreenshotResult") {
        Ok(c) => c,
        Err(e) => {
            throw(&mut env, format!("ScreenshotResult class not found: {e}"));
            return std::ptr::null_mut();
        }
    };

    let array: JObjectArray =
        match env.new_object_array(results.len() as i32, &class, JObject::null()) {
            Ok(a) => a,
            Err(e) => {
                throw(&mut env, format!("failed to allocate result array: {e}"));
                return std::ptr::null_mut();
            }
        };

    for (i, r) in results.iter().enumerate() {
        let img = match env.byte_array_from_slice(&r.image_bytes) {
            Ok(a) => a,
            Err(e) => {
                throw(&mut env, format!("failed to build image bytes: {e}"));
                return std::ptr::null_mut();
            }
        };
        let img_obj: JObject = img.into();
        let obj = match env.new_object(
            &class,
            "(III[B)V",
            &[
                JValue::Int(r.page_num as i32),
                JValue::Int(r.width as i32),
                JValue::Int(r.height as i32),
                JValue::Object(&img_obj),
            ],
        ) {
            Ok(o) => o,
            Err(e) => {
                throw(&mut env, format!("failed to build ScreenshotResult: {e}"));
                return std::ptr::null_mut();
            }
        };
        if let Err(e) = env.set_object_array_element(&array, i as i32, &obj) {
            throw(&mut env, format!("failed to set array element: {e}"));
            return std::ptr::null_mut();
        }
    }

    array.into_raw()
}

/// Search text items for phrase matches. Input/output are JSON arrays of TextItem.
#[no_mangle]
pub extern "system" fn Java_io_liteparse_NativeBindings_nativeSearchItems<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    items_json: JString<'local>,
    phrase: JString<'local>,
    case_sensitive: jni::sys::jboolean,
) -> jstring {
    let items_json: String = match env.get_string(&items_json) {
        Ok(s) => s.into(),
        Err(e) => {
            throw(&mut env, format!("failed to read items JSON: {e}"));
            return std::ptr::null_mut();
        }
    };
    let phrase: String = match env.get_string(&phrase) {
        Ok(s) => s.into(),
        Err(e) => {
            throw(&mut env, format!("failed to read phrase: {e}"));
            return std::ptr::null_mut();
        }
    };

    let dto_items: Vec<DtoTextItem> = match serde_json::from_str(&items_json) {
        Ok(v) => v,
        Err(e) => {
            throw(&mut env, format!("invalid items JSON: {e}"));
            return std::ptr::null_mut();
        }
    };
    let core_items: Vec<TextItem> = dto_items.iter().map(DtoTextItem::to_core).collect();

    let options = SearchOptions {
        phrase,
        case_sensitive: case_sensitive != 0,
    };
    let merged = search_items(&core_items, &options);
    let out: Vec<DtoTextItem> = merged.iter().map(DtoTextItem::from_core).collect();

    match serde_json::to_string(&out) {
        Ok(s) => match env.new_string(s) {
            Ok(js) => js.into_raw(),
            Err(e) => {
                throw(&mut env, format!("failed to build result string: {e}"));
                std::ptr::null_mut()
            }
        },
        Err(e) => {
            throw(&mut env, format!("failed to serialize search result: {e}"));
            std::ptr::null_mut()
        }
    }
}
