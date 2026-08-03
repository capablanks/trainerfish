import re
import runpy

_original_compile = re.compile


def _fixed_compile(pattern, *args, **kwargs):
    if isinstance(pattern, str) and "LichessBroadcastDialog" in pattern:
        pattern = pattern.replace("\\\\", "\\")
    return _original_compile(pattern, *args, **kwargs)


re.compile = _fixed_compile
runpy.run_path(
    "tools/apply_chess_tv_landscape_dialog_save_icon.py",
    run_name="__main__",
)
