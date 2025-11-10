#!/bin/bash

source ./config.env

# Obtenir configuració dels paràmetres
USER=${1:-$DEFAULT_USER}
RSA_PATH=${2:-$DEFAULT_RSA_PATH}
SERVER_PORT=${3:-$DEFAULT_SERVER_PORT}
SSH_OPTS='-oHostKeyAlgorithms=+ssh-rsa -oPubkeyAcceptedAlgorithms=+ssh-rsa'

echo "User: $USER"
echo "Ruta RSA: $RSA_PATH"
echo "Server port: $SERVER_PORT"

JAR_NAME="server-package.jar"

cd ..

# Comprovem que els arxius existeixen
if [[ ! -f "$RSA_PATH" ]]; then
  echo "Error: No s'ha trobat el fitxer de clau privada: $RSA_PATH"
  cd proxmox
  exit 1
fi

# Iniciar ssh-agent i carregar la clau RSA
eval "$(ssh-agent -s)"
ssh-add "$RSA_PATH"

# SSH al servidor per trobar i matar el procés del JAR
ssh -t -p 20127 $SSH_OPTS "$USER@ieticloudpro.ieti.cat" << EOF
    PID=\$(ps aux | grep 'java -jar $JAR_NAME' | grep -v 'grep' | awk '{print \$2}')
    if [ -n "\$PID" ]; then
      # Envia un senyal de terminació suau
      kill -15 \$PID
      echo "Senyal SIGTERM enviat al procés \$PID."
      
      # Espera que el procés acabi correctament
      for i in {1..10}; do
        if ! ps -p \$PID > /dev/null; then
          echo "Procés \$PID aturat correctament."
          break
        fi
        echo "Esperant que el procés finalitzi..."
        sleep 1
      done

      # Força la terminació si encara està actiu
      if ps -p \$PID > /dev/null; then
        echo "Procés \$PID encara actiu, forçant aturada..."
        kill -9 \$PID
      fi
    else
      echo "No s'ha trobat el procés $JAR_NAME."
    fi

    # Comprova si el port està ocupat i espera fins que es desalliberi
    while netstat -an | grep -q ':$SERVER_PORT.*LISTEN'; do
      echo "Esperant que el port $SERVER_PORT es desalliberi..."
      sleep 1
    done

    echo "Port $SERVER_PORT desalliberat."
EOF

# Finalitzar l'agent SSH
ssh-agent -k

cd proxmox