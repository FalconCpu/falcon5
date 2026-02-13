#!/usr/bin/env python3
"""
Reorganize LANGUAGE_GUIDE.md to move advanced type system sections later.
This script will move sections 4, 5, 6 to after section 17 (Ranges).
"""

def read_file(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        return f.read()

def write_file(filename, content):
    with open(filename, 'w', encoding='utf-8') as f:
        f.write(content)

def extract_section(content, start_marker, end_marker):
    """Extract a section from content between start and end markers."""
    start_idx = content.find(start_marker)
    if start_idx == -1:
        return None, content

    # Find the next ## header after start_marker
    search_start = start_idx + len(start_marker)
    end_idx = content.find('\n## ', search_start)

    if end_idx == -1:
        # This is the last section
        section = content[start_idx:]
        remaining = content[:start_idx]
    else:
        section = content[start_idx:end_idx]
        remaining = content[:start_idx] + content[end_idx:]

    return section, remaining

# Read the backup (which has the duck typing additions but is corrupted)
content = read_file('LANGUAGE_GUIDE.md.backup')

print("File has been corrupted by previous edits.")
print("The user wants sections 4, 5, 6 moved to after Ranges (section 17).")
print("However, the file structure is broken. We need to manually fix it.")
print("\nPlease restore the file manually or from backup.")

