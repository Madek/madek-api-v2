set -euo pipefail

ENV_DIR="$(cd -- "$(dirname "${BASH_SOURCE}")"; cd . > /dev/null 2>&1 && pwd -P)"

if [ -z "${TOOL_VERSIONS_MANAGER:-}" ]; then
    export TOOL_VERSIONS_MANAGER="asdf"
fi

case "$TOOL_VERSIONS_MANAGER" in
  asdf|asdf-vm)
    source "$ENV_DIR/asdf-helper.bash"
    asdf-load
    echo "Using asdf $(asdf --version) as TOOL_VERSIONS_MANAGER"
    ;;
  mise)
    if type "mise" &> /dev/null; then
      # TODO maybe install and setup mise to include in path automatically here
      echo "Using mise $(mise --version) as TOOL_VERSIONS_MANAGER"
    else
      echo "ERROR: mise is not installed but TOOL_VERSIONS_MANAGER is set to 'mise'"
      exit 1
    fi
    ;;
  *)
    echo "ERROR: Unsupported TOOL_VERSIONS_MANAGER: $TOOL_VERSIONS_MANAGER"
    exit 1
    ;;
esac

