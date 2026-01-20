#!/bin/bash

set -e

SRC_LOCATION=quickjs

# Check if URL provided
if [ -z "$1" ]; then
    echo "Error: Please provide a QuickJS release URL"
    echo "Usage: $0 <url>"
    echo "Example: $0 https://bellard.org/quickjs/quickjs-2024-01-13.tar.xz"
    exit 1
fi

# Download the things: provide a link to the release binary (e.g. https://bellard.org/quickjs/quickjs-2019-07-09.tar.xz)
echo "Downloading QuickJS from: $1"
wget -O quickjs.tar.xz $1

# Extract the release
echo "Extracting archive..."
mkdir tmp
tar xvfC quickjs.tar.xz tmp/

# Find the extracted directory
EXTRACTED_DIR=$(find tmp -maxdepth 1 -type d -name "quickjs-*" | head -n 1)

if [ -z "$EXTRACTED_DIR" ]; then
    echo "Error: Could not find extracted quickjs directory"
    rm quickjs.tar.xz
    rm -r tmp
    exit 1
fi

echo ""
echo "==================================================================="
echo "COMPARISON: Current vs New QuickJS files"
echo "==================================================================="
echo ""

# Show what files exist in current location
echo "Files in CURRENT $SRC_LOCATION/ directory:"
if [ -d "$SRC_LOCATION" ]; then
    ls -1 "$SRC_LOCATION" | sort
else
    echo "(directory does not exist yet)"
fi

echo ""
echo "-------------------------------------------------------------------"
echo ""

# Show what files will be copied from new download
echo "Files in DOWNLOADED archive:"
ls -1 "$EXTRACTED_DIR" | sort

echo ""
echo "==================================================================="
echo ""

# Show detailed diff
echo "DETAILED ANALYSIS:"
echo ""

# Files that will be ADDED (in download but not in current)
echo "Files that will be ADDED or UPDATED:"
if [ -d "$SRC_LOCATION" ]; then
    comm -13 <(ls -1 "$SRC_LOCATION" 2>/dev/null | sort) <(ls -1 "$EXTRACTED_DIR" | sort) | sed 's/^/  + /'
    echo ""
    echo "Files that exist in both (will be OVERWRITTEN):"
    comm -12 <(ls -1 "$SRC_LOCATION" 2>/dev/null | sort) <(ls -1 "$EXTRACTED_DIR" | sort) | sed 's/^/  ~ /'
else
    ls -1 "$EXTRACTED_DIR" | sed 's/^/  + /'
fi

echo ""

# Files that will be DELETED (in current but not in download)
if [ -d "$SRC_LOCATION" ]; then
    DELETED_FILES=$(comm -23 <(ls -1 "$SRC_LOCATION" 2>/dev/null | sort) <(ls -1 "$EXTRACTED_DIR" | sort))
    if [ -n "$DELETED_FILES" ]; then
        echo "⚠️  WARNING: Files that will be DELETED:"
        echo "$DELETED_FILES" | sed 's/^/  - /'
        echo ""
    fi
fi

echo "==================================================================="
echo ""

# Validate that critical files required by CMakeLists.txt are present
echo "VALIDATING: Checking for files required by CMakeLists.txt..."
REQUIRED_FILES=(
    "quickjs.c"
    "libbf.c"
    "quickjs-libc.c"
    "quickjs-debugger.c"
    "quickjs-debugger-transport-unix.c"
    "libunicode.c"
    "libregexp.c"
    "cutils.c"
)

MISSING_FILES=()
for file in "${REQUIRED_FILES[@]}"; do
    if [ ! -f "$EXTRACTED_DIR/$file" ]; then
        MISSING_FILES+=("$file")
    fi
done

if [ ${#MISSING_FILES[@]} -gt 0 ]; then
    echo ""
    echo "❌ ERROR: The downloaded archive is missing critical files required by CMakeLists.txt:"
    for file in "${MISSING_FILES[@]}"; do
        echo "  - $file"
    done
    echo ""
    echo "This QuickJS release is incompatible with your build system."
    echo "Cleaning up and aborting..."
    rm quickjs.tar.xz
    rm -r tmp
    exit 1
fi

echo "✓ All required files are present in the download"
echo ""
echo "==================================================================="
echo ""

# Ask for confirmation
read -p "Do you want to proceed with the update? (yes/no): " CONFIRM

if [ "$CONFIRM" != "yes" ]; then
    echo "Update cancelled. Cleaning up..."
    rm quickjs.tar.xz
    rm -r tmp
    exit 0
fi

echo ""
echo "Proceeding with update..."

# Clear target location
rm -rf $SRC_LOCATION/*

# Copy all files from extracted archive (excluding directories for now)
echo "Copying files..."
find "$EXTRACTED_DIR/" -maxdepth 1 -type f -exec cp {} $SRC_LOCATION/ \;

# Copy directories if they exist
if [ -d "$EXTRACTED_DIR/doc" ]; then
    cp -r "$EXTRACTED_DIR/doc" $SRC_LOCATION/
fi
if [ -d "$EXTRACTED_DIR/examples" ]; then
    cp -r "$EXTRACTED_DIR/examples" $SRC_LOCATION/
fi
if [ -d "$EXTRACTED_DIR/tests" ]; then
    cp -r "$EXTRACTED_DIR/tests" $SRC_LOCATION/
fi

# Cleanup after ourselves
echo "Cleaning up..."
rm quickjs.tar.xz
rm -r tmp

echo ""
echo "✓ Update completed successfully!"
echo ""
echo "Updated files in $SRC_LOCATION/"
ls -1 "$SRC_LOCATION" | wc -l | xargs echo "Total items:"
