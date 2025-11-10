#!/bin/bash
# Setup NAT redirect: 80 -> $SERVER_PORT (idempotent, password-safe)

set -euo pipefail
source ./config.env

USER=${1:-$DEFAULT_USER}
RSA_PATH=${2:-"$HOME/.ssh/id_rsa"}
SERVER_PORT=${3:-$DEFAULT_SERVER_PORT}
RSA_PATH="${RSA_PATH%$'\r'}"
SSH_OPTS='-oIdentitiesOnly=yes -oHostKeyAlgorithms=+ssh-rsa -oPubkeyAcceptedAlgorithms=+ssh-rsa'

echo "Server port: $SERVER_PORT"
[[ -f "$RSA_PATH" ]] || { echo "Error: no troba la clau: $RSA_PATH"; exit 1; }

read -s -p "Pwd sudo remota: " SUDO_PASSWORD
echo
ESC_PWD=$(printf "%q" "$SUDO_PASSWORD")

eval "$(ssh-agent -s)" >/dev/null
ssh-add "$RSA_PATH" >/dev/null

# Prepare remote script with real port substituted
REMOTE_SCRIPT=$(cat <<'EOF'
set -euo pipefail
run_sudo() { echo "$SUDO_PASSWORD" | sudo -S -p '' "$@"; }

export DEBIAN_FRONTEND=noninteractive
run_sudo apt-get update -qq
run_sudo apt-get install -y -qq iptables-persistent >/dev/null

# Idempotent check
if run_sudo iptables -t nat -C PREROUTING -p tcp --dport 80 -j REDIRECT --to-ports SERVER_PORT_VALUE 2>/dev/null; then
  echo "Ja existeix la redirecció 80 -> SERVER_PORT_VALUE"
else
  echo "Afegint redirecció 80 -> SERVER_PORT_VALUE..."
  run_sudo iptables -t nat -A PREROUTING -p tcp --dport 80 -j REDIRECT --to-ports SERVER_PORT_VALUE
fi

# Persist
TMP=$(mktemp)
run_sudo iptables-save > "$TMP"
run_sudo install -m 600 "$TMP" /etc/iptables/rules.v4
rm -f "$TMP"
command -v systemctl >/dev/null 2>&1 && run_sudo systemctl restart netfilter-persistent || true

echo "✔︎ Redirecció 80 -> SERVER_PORT_VALUE configurada i persistida."
EOF
)

# Substitute port before sending
REMOTE_SCRIPT="${REMOTE_SCRIPT//SERVER_PORT_VALUE/$SERVER_PORT}"

ssh -T -p 20127 $SSH_OPTS "$USER@ieticloudpro.ieti.cat" "SUDO_PASSWORD=$ESC_PWD bash -s" <<<"$REMOTE_SCRIPT"

ssh-agent -k >/dev/null
