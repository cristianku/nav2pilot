#!/usr/bin/env bash
# Builds a signed release APK of Nav2Pilot locally, with the same key as the GitHub releases,
# then publishes it: pushes the tag v<version>, and the GitHub workflow creates the Release.
#
#   scripts/build-release.sh              next version after the latest tag (v1.0.4 -> 1.0.5)
#   scripts/build-release.sh 1.0.6        a given version
#   scripts/build-release.sh 1.0.6 --install   also installs it on the phone connected with adb
#   scripts/build-release.sh --local      only builds, does not publish
#
# Needs keystore.properties and the keystore in .signing/ (both ignored by git, see README).
# The APK ends up in build/nav2pilot-v<version>.apk.
set -euo pipefail

REPO="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO"

# certificate of the permanent release key (v1.0.3 and later)
EXPECTED_CERT="79be28ef7cc9118c3547a28fbc327f776e8bff7aeb37705be7ff01c6ba5d4164"

VERSION=""
INSTALL="no"
PUBLISH="yes"
for ARG in "$@"; do
  if [ "$ARG" = "--install" ]; then
    INSTALL="yes"
  elif [ "$ARG" = "--local" ]; then
    PUBLISH="no"
  else
    VERSION="$ARG"
  fi
done

if [ -z "$VERSION" ]; then
  LAST_TAG="$(git tag --list 'v*' --sort=-v:refname | head -1)"
  if [ -z "$LAST_TAG" ]; then
    echo "No v* tag found: pass the version, e.g. scripts/build-release.sh 1.0.0"
    exit 1
  fi
  LAST="${LAST_TAG#v}"
  MAJOR="$(echo "$LAST" | cut -d. -f1)"
  MINOR="$(echo "$LAST" | cut -d. -f2)"
  PATCH="$(echo "$LAST" | cut -d. -f3)"
  if [ -z "$PATCH" ]; then
    PATCH=0
  fi
  VERSION="$MAJOR.$MINOR.$((PATCH + 1))"
  echo "Latest tag $LAST_TAG -> building $VERSION"
fi

TAG="v$VERSION"

if [ "$PUBLISH" = "yes" ]; then
  # the release is built by GitHub from the tagged commit: it must be committed and pushed
  if [ -n "$(git status --porcelain)" ]; then
    echo "Uncommitted changes: commit them first, the release is built from the pushed commit."
    exit 1
  fi
  if git rev-parse -q --verify "refs/tags/$TAG" > /dev/null; then
    echo "Tag $TAG already exists."
    exit 1
  fi
  BRANCH="$(git rev-parse --abbrev-ref HEAD)"
  git fetch -q origin "$BRANCH"
  if [ "$(git rev-parse HEAD)" != "$(git rev-parse "origin/$BRANCH")" ]; then
    echo "$BRANCH is not in sync with origin/$BRANCH: push (or pull) first."
    exit 1
  fi
fi

if [ ! -f keystore.properties ]; then
  echo "keystore.properties is missing: without it the APK cannot be signed with the release key."
  exit 1
fi

# the pinned Android Gradle Plugin 8.0 needs JDK 17
JAVA_HOME="$(/usr/libexec/java_home -v 17)"
export JAVA_HOME

SDK="$HOME/Library/Android/sdk"
BUILD_TOOLS="$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)"

echo "== tests and signed release build ($VERSION)"
./gradlew testDebugUnitTest assembleRelease -PreleaseVersion="$VERSION"

APK_BUILT="app/build/outputs/apk/release/app-release.apk"
APK="build/nav2pilot-v$VERSION.apk"
mkdir -p build
cp "$APK_BUILT" "$APK"

echo "== checks"
CERT="$("$BUILD_TOOLS/apksigner" verify --print-certs "$APK" | grep 'SHA-256 digest' | head -1 | awk '{print $NF}')"
if [ "$CERT" != "$EXPECTED_CERT" ]; then
  echo "WRONG SIGNING KEY: $CERT (expected $EXPECTED_CERT). It would not install over the published releases."
  exit 1
fi
echo "signed with the release key: ok"
"$BUILD_TOOLS/aapt" dump badging "$APK" | head -1 | grep -o "versionCode='[0-9]*' versionName='[^']*'"

if [ "$INSTALL" = "yes" ]; then
  echo "== install on the phone"
  "$SDK/platform-tools/adb" install -r "$APK"
fi

echo
echo "APK: $REPO/$APK"

if [ "$PUBLISH" = "yes" ]; then
  echo
  echo "== publish $TAG"
  git tag "$TAG"
  git push origin "$TAG"
  echo "GitHub is building and publishing the release:"
  echo "  https://github.com/cristianku/nav2pilot/actions"
  echo "  https://github.com/cristianku/nav2pilot/releases/tag/$TAG"
fi
