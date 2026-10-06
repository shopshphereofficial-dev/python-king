import io
import sys
import traceback


def run_code(code):
    """Run the given Python source and return everything it printed.

    stdout and stderr are captured, and any exception is turned into a
    normal traceback string so the app can show it in the output panel.
    """
    buf = io.StringIO()
    old_out, old_err, old_in = sys.stdout, sys.stderr, sys.stdin
    sys.stdout = buf
    sys.stderr = buf
    sys.stdin = io.StringIO("")
    try:
        compiled = compile(code, "<script>", "exec")
        glob = {"__name__": "__main__"}
        exec(compiled, glob)
    except SystemExit as e:
        if e.code is not None:
            buf.write("SystemExit: %s\n" % (e.code,))
    except BaseException:
        traceback.print_exc(file=buf)
    finally:
        sys.stdout = old_out
        sys.stderr = old_err
        sys.stdin = old_in

    out = buf.getvalue()
    if out and not out.endswith("\n"):
        out += "\n"
    return out
