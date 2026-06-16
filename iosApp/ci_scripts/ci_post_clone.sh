#!/bin/sh
# Xcode Cloud runs this automatically after cloning the repo.
#
# 1. Writes SecretsRelease.xcconfig from environment variables defined
#    in App Store Connect → Xcode Cloud → Workflow → Environment.
#    Required env vars:
#      ADMOB_APP_ID
#      ADMOB_REWARDED_UNIT_ID
#
# 2. Installs a JDK so the Gradle build phase (which builds the KMP
#    iOS framework) can run. Xcode Cloud's image does not include a JDK.

set -e

# --- 1. SecretsRelease.xcconfig ------------------------------------------------

if [ -z "$ADMOB_APP_ID" ] || [ -z "$ADMOB_REWARDED_UNIT_ID" ]; then
  echo "error: ADMOB_APP_ID and ADMOB_REWARDED_UNIT_ID must be set in Xcode Cloud environment."
  exit 1
fi

SECRETS_PATH="$CI_PRIMARY_REPOSITORY_PATH/iosApp/SecretsRelease.xcconfig"

cat > "$SECRETS_PATH" <<EOF
ADMOB_APP_ID = $ADMOB_APP_ID
ADMOB_REWARDED_UNIT_ID = $ADMOB_REWARDED_UNIT_ID
EOF

echo "Wrote $SECRETS_PATH"

# --- 2. JDK for the Gradle build phase ----------------------------------------

if /usr/libexec/java_home >/dev/null 2>&1; then
  echo "JDK already present: $(/usr/libexec/java_home)"
else
  echo "Installing openjdk@17 via Homebrew..."
  brew install openjdk@17
  echo "openjdk@17 installed."
fi
