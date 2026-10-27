#!/bin/zsh
cd -- "$(dirname -- "$0")" || exit 1

if command -v node >/dev/null 2>&1; then
  exec node servidor.js
fi

exec "$HOME/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node" servidor.js
