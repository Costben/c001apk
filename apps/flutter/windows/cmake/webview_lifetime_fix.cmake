# flutter_inappwebview_windows 0.6.0 keeps WinRT composition resources in
# inline statics. Releasing them during DLL_PROCESS_DETACH happens after the
# dispatcher is gone and fails fast in coremessaging (status 0xF0464645).
# Compile a narrowly patched copy; never modify the global pub cache.
set(_webview_target flutter_inappwebview_windows_plugin)
if(NOT TARGET ${_webview_target})
  message(FATAL_ERROR "Windows WebView target missing; review the lifetime fix")
endif()
get_target_property(_webview_dir ${_webview_target} SOURCE_DIR)
set(_manager_relative "in_app_webview/in_app_webview_manager.cpp")
file(READ "${_webview_dir}/${_manager_relative}" _manager_source)
set(_cleanup_anchor "    windowWebViews.clear();")
string(FIND "${_manager_source}" "${_cleanup_anchor}" _cleanup_offset)
if(_cleanup_offset EQUAL -1)
  message(FATAL_ERROR "Windows WebView source changed; review the lifetime fix")
endif()
string(REPLACE "${_cleanup_anchor}" "${_cleanup_anchor}
    // Release while the platform thread and its dispatcher are still alive.
    compositor_ = nullptr;
    graphics_context_ = nullptr;
    dispatcher_queue_controller_ = nullptr;
    rohelper_ = nullptr;
    valid_ = false;" _manager_source "${_manager_source}")
set(_patched_dir "${CMAKE_CURRENT_BINARY_DIR}/webview_lifetime_fix")
file(MAKE_DIRECTORY "${_patched_dir}")
set(_patched_source "${_patched_dir}/in_app_webview_manager.cpp")
file(WRITE "${_patched_source}" "${_manager_source}")
get_target_property(_webview_sources ${_webview_target} SOURCES)
set(_fixed_sources "")
set(_replaced FALSE)
foreach(_source IN LISTS _webview_sources)
  if(_source STREQUAL _manager_relative OR
      _source STREQUAL "${_webview_dir}/${_manager_relative}")
    list(APPEND _fixed_sources "${_patched_source}")
    set(_replaced TRUE)
  elseif(IS_ABSOLUTE "${_source}" OR _source MATCHES "^\\$<")
    list(APPEND _fixed_sources "${_source}")
  else()
    list(APPEND _fixed_sources "${_webview_dir}/${_source}")
  endif()
endforeach()
if(NOT _replaced)
  message(FATAL_ERROR "Windows WebView manager source missing; review the lifetime fix")
endif()
set_property(TARGET ${_webview_target} PROPERTY SOURCES "${_fixed_sources}")
target_include_directories(${_webview_target} PRIVATE
  "${_webview_dir}/in_app_webview")
