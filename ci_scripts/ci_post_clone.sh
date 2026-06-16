#!/bin/sh
# Xcode Cloud runs this automatically after cloning the repo.
# It writes SecretsRelease.xcconfig from environment variables defined
# in App Store Connect → Xcode Cloud → Workflow → Environment.
#
# Required environment variables:
#   ADMOB_APP_ID
#   ADMOB_REWARDED_UNIT_ID

set -e

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
