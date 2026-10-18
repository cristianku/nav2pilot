#!/usr/bin/env bash
# One-time: exports the release key of the GitHub releases for Play App Signing, so the apps installed from
# GitHub and from Google Play have the same signature and update each other.
#
# Before running it, in Play Console: Test and release -> App integrity -> App signing ->
# "Export and upload a key from Java keystore", and download pepk.jar and encryption_public_key.pem
# (they are looked for in ~/Downloads, then in build/play-signing/).
#
#   scripts/play-export-signing-key.sh
#
# Writes build/play-signing/nav2pilot-signing.zip (ignored by git), opens it in Finder for the upload,
# then deletes it together with pepk.jar and the encryption key.
set -euo pipefail

REPO="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO"

WORK="build/play-signing"
OUTPUT="$WORK/nav2pilot-signing.zip"
mkdir -p "$WORK"

if [ ! -f keystore.properties ]; then
  echo "keystore.properties not found in $REPO"
  exit 1
fi

# keystore.properties: storeFile, storePassword, keyAlias, keyPassword
property() {
  grep -E "^$1=" keystore.properties | head -1 | cut -d= -f2-
}
STORE_FILE="$(property storeFile)"
STORE_PASSWORD="$(property storePassword)"
KEY_ALIAS="$(property keyAlias)"
KEY_PASSWORD="$(property keyPassword)"

if [ ! -f "$STORE_FILE" ]; then
  echo "Keystore $STORE_FILE not found"
  exit 1
fi

# the files downloaded from Play Console: from ~/Downloads into the work folder
for NAME in pepk.jar encryption_public_key.pem; do
  if [ -f "$HOME/Downloads/$NAME" ]; then
    mv "$HOME/Downloads/$NAME" "$WORK/$NAME"
  fi
  if [ ! -f "$WORK/$NAME" ]; then
    echo "$NAME not found in ~/Downloads or $WORK: download it from Play Console (App signing page)."
    exit 1
  fi
done

# pepk asks for the two passwords: the keystore password first, then the key password
echo
if [ "$STORE_PASSWORD" = "$KEY_PASSWORD" ]; then
  printf "%s" "$STORE_PASSWORD" | pbcopy
  echo "The password is in the clipboard: paste it (Cmd+V) and press Enter at BOTH prompts."
else
  printf "%s" "$STORE_PASSWORD" | pbcopy
  echo "The keystore password is in the clipboard: paste it (Cmd+V) at the FIRST prompt."
  echo "At the SECOND prompt type the key password (keyPassword in keystore.properties)."
fi
echo

rm -f "$OUTPUT"
java -jar "$WORK/pepk.jar" \
  --keystore="$STORE_FILE" \
  --alias="$KEY_ALIAS" \
  --output="$OUTPUT" \
  --include-cert \
  --rsa-aes-encryption \
  --encryption-key-path="$WORK/encryption_public_key.pem"

# the password must not stay in the clipboard
printf "" | pbcopy

if [ ! -f "$OUTPUT" ]; then
  echo "pepk did not create $OUTPUT"
  exit 1
fi

echo
echo "Created $OUTPUT"
echo "Upload it in Play Console, on the same App signing page, and save."
open -R "$OUTPUT"
echo
read -r -p "Press Enter after the upload to delete the temporary files... " _
rm -f "$OUTPUT" "$WORK/pepk.jar" "$WORK/encryption_public_key.pem"
echo "Temporary files deleted. The keystore in $STORE_FILE is unchanged."
