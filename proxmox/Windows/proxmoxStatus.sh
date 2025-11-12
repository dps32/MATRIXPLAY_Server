#!/bin/bash

# Script to check the status of the server

source ../config.env

USER=${1:-$DEFAULT_USER}
RSA_PATH=${2:-"$DEFAULT_RSA_PATH"}
RSA_PATH="${RSA_PATH%$'\r'}"
RSA_PATH="${RSA_PATH/#\~/$HOME}"
SERVER_PORT=${3:-$DEFAULT_SERVER_PORT}
SSH_OPTS='-oHostKeyAlgorithms=+ssh-rsa -oPubkeyAcceptedAlgorithms=+ssh-rsa'

echo "Comprovant estat del servidor..."

if [[ ! -f "${RSA_PATH}" ]]; then
    echo "Error: No s'ha trobat el fitxer de clau privada: $RSA_PATH"
    exit 1
fi

ssh -i "${RSA_PATH}" -p 20127 $SSH_OPTS "$USER@ieticloudpro.ieti.cat" << 'EOF'
    echo "=== Processos Java actius ==="
    ps aux | grep 'java -jar' | grep -v 'grep'
    
    echo ""
    echo "=== Ports en escolta ==="
    netstat -tuln | grep LISTEN | grep ':3000'
    
    echo ""
    echo "=== Últimes línies del log ==="
    if [ -f ~/output.log ]; then
        tail -20 ~/output.log
    else
        echo "No s'ha trobat el fitxer output.log"
    fi
EOF

echo ""
echo "Comprovació completada!"
