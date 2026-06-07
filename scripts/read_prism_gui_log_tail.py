#!/usr/bin/env python3
import argparse
import sys

import gi

gi.require_version("Atspi", "2.0")
from gi.repository import Atspi


def safe(call, default=None):
    try:
        return call()
    except Exception:
        return default


def children(accessible):
    count = safe(accessible.get_child_count, 0) or 0
    for index in range(count):
        child = safe(lambda index=index: accessible.get_child_at_index(index))
        if child is not None:
            yield child


def text_character_count(accessible):
    try:
        return Atspi.Text.get_character_count(accessible)
    except Exception:
        return 0


def find_prism_application():
    desktop = Atspi.get_desktop(0)
    for application in children(desktop):
        name = safe(application.get_name, "") or ""
        if "prism" in name.lower():
            return application
    return None


def find_largest_text(accessible):
    best = (0, None)
    stack = [(accessible, 0)]
    while stack:
        current, depth = stack.pop()
        char_count = text_character_count(current)
        if char_count > best[0]:
            best = (char_count, current)
        if depth >= 10:
            continue
        for child in children(current):
            stack.append((child, depth + 1))
    return best


def main():
    parser = argparse.ArgumentParser(description="Read Prism Launcher's Minecraft Log widget through AT-SPI.")
    parser.add_argument("--count", action="store_true", help="Print the current character count only.")
    parser.add_argument("--start", type=int, default=0, help="Only print text after this character offset.")
    parser.add_argument("--max-chars", type=int, default=262144, help="Maximum characters to print from the end.")
    args = parser.parse_args()

    application = find_prism_application()
    if application is None:
        return 2

    char_count, log_widget = find_largest_text(application)
    if log_widget is None or char_count <= 0:
        return 3

    if args.count:
        print(char_count)
        return 0

    start = max(0, args.start)
    if start > char_count:
        start = 0
    start = max(start, char_count - max(1, args.max_chars))

    try:
        text = Atspi.Text.get_text(log_widget, start, char_count)
    except Exception as exception:
        print(f"failed to read Prism GUI log text: {exception}", file=sys.stderr)
        return 4

    sys.stdout.write(text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
