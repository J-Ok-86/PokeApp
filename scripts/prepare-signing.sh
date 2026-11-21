#!/usr/bin/env bash
# Prepare a one-time signing key for repeatable GitHub Actions APK updates.
# Key private material stays in your Codespace and encrypted GitHub Actions secret.
set -euo pipefail
REPO="J-Ok-86/PokeApp"
SECRET="POKEHABIT_DEBUG_KEYSTORE_B64"
KEY_DIR="$HOME/.config/pokehabit-private"
KEY_FILE="$KEY_DIR/debug.keystore"

if ! command -v keytool >/dev/null 2>&1; then
  echo 'ERROR: Java keytool is missing. Install JDK 17 in this Codespace first.' >&2
  exit 1
fi
if ! command -v gh >/dev/null 2>&1; then
  echo 'ERROR: GitHub CLI (gh) is missing.' >&2
  exit 1
fi

# IMPORTANT: Do not overwrite an already-uploaded key with a new key.
if ! existing=$(gh secret list -R "$REPO" --json name --jq '.[].name' 2>&1); then
  echo 'ERROR: Cannot list GitHub Actions secrets. Grant the Codespace token access to manage repository Actions secrets, or add the secret manually.' >&2
  echo "$existing" >&2
  exit 1
fi
if printf '%s\n' "$existing" | grep -qx "$SECRET"; then
  echo "ALREADY CONFIGURED: $SECRET exists in GitHub Secrets."
  echo 'Do NOT generate a different key. Use the current secret for all future builds.'
  exit 0
fi

mkdir -p "$KEY_DIR"
chmod 700 "$KEY_DIR"
if [ ! -f "$KEY_FILE" ]; then
  keytool -genkeypair -noprompt -keystore "$KEY_FILE" -storetype JKS \
    -alias androiddebugkey -keyalg RSA -keysize 3072 -validity 36500 \
    -storepass android -keypass android \
    -dname 'CN=PokeHabit APK Signing, O=Personal Use'
  chmod 600 "$KEY_FILE"
fi

# stdin avoids displaying the private key in terminal output or history.
base64 -w 0 "$KEY_FILE" | gh secret set "$SECRET" -R "$REPO"
echo "SUCCESS: $SECRET stored in GitHub Actions Secrets for $REPO"
echo 'Keep a PRIVATE backup of the keystore. Never commit it into the public repository.'
echo "Keystore: $KEY_FILE"
