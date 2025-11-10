#!/bin/bash
# Remove only NAT PREROUTING REDIRECT rules for dport 80 and persist.

set -euo pipefail

source ./config.env

USER=${1:-$DEFAULT_USER}
RSA_PATH=${2:-"$DEFAULT_RSA_PATH"}
SERVER_PORT=${3:-$DEFAULT_SERVER_PORT}  # not required; we remove ALL 80 redirects
RSA_PATH="${RSA_PATH%$'\r'}"
SSH_OPTS='-oIdentitiesOnly=yes -oHostKeyAlgorithms=+ssh-rsa -oPubkeyAcceptedAlgorithms=+ssh-rsa'

echo "User: $USER"
echo "RSA:  $RSA_PATH"

[[ -f "$RSA_PATH" ]] || { echo "Error: no troba la clau: $RSA_PATH"; exit 1; }

read -s -p "Pwd sudo remota: " SUDO_PASSWORD
echo

# Escape password safely for embedding
ESC_PWD=$(printf "%q" "$SUDO_PASSWORD")

eval "$(ssh-agent -s)" >/dev/null
ssh-add "$RSA_PATH" >/dev/null

# -T: no TTY. sudo reads from stdin via -S.
ssh -T -p 20127 $SSH_OPTS "$USER@ieticloudpro.ieti.cat" <<EOF
set -euo pipefail
SUDO_PASSWORD='$ESC_PWD'   # injected locally

# Sanity check: ensure sudo works non-interactively
echo "\$SUDO_PASSWORD" | sudo -S -k true >/dev/null 2>&1

# List candidate NAT rules for dport 80 with REDIRECT
CANDIDATES=\$(echo "\$SUDO_PASSWORD" | sudo -S iptables-save -t nat | awk '
  BEGIN{inNat=0}
  /^\*nat/{inNat=1; next}
  /^COMMIT/{inNat=0}
  inNat && /-A PREROUTING/ && /--dport 80/ && /-j REDIRECT/ {print}
')

if [[ -z "\$CANDIDATES" ]]; then
  echo "No hi ha redireccions de port 80 a eliminar."
else
  echo "Eliminant redireccions de port 80:"
  while read -r LINE; do
    [[ -z "\$LINE" ]] && continue
    CMD=\$(echo "\$LINE" | sed 's/^-A PREROUTING/-D PREROUTING/')
    echo " - iptables -t nat \$CMD"
    echo "\$SUDO_PASSWORD" | sudo -S iptables -t nat \$CMD
  done <<< "\$CANDIDATES"
fi

# Persist full rules (no deletes of rules.v4)
TMP=\$(mktemp)
echo "\$SUDO_PASSWORD" | sudo -S iptables-save > "\$TMP"
echo "\$SUDO_PASSWORD" | sudo -S install -m 600 "\$TMP" /etc/iptables/rules.v4
rm -f "\$TMP"
echo "\$SUDO_PASSWORD" | sudo -S systemctl restart netfilter-persistent || true

echo "✔︎  Redireccions del port 80 eliminades i configuració persistent actualitzada."
EOF

ssh-agent -k >/dev/null
