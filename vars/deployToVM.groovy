def call(targetIP, targetPort, envName) {
    withCredentials([
        usernamePassword(credentialsId: env.ACR_CRED_ID, usernameVariable: 'ACR_USR', passwordVariable: 'ACR_PSW'),
        string(credentialsId: env.RABBIT_CRED_ID, variable: 'RABBIT_PASS')
    ]) {
        sshagent(["${env.SSH_CRED_ID}"]) {
            sh """
                ssh -o StrictHostKeyChecking=no azureuser@${targetIP} << 'EOF'
                    set -e
                    mkdir -p ~/deployments/${envName}
                    cd ~/deployments/${envName}
                    
                    # 1. Login
                    echo "${ACR_PSW}" | sudo docker login ${env.ACR_URL} -u "${ACR_USR}" --password-stdin
                    
                    # 2. Backup purani .env file rollback ke liye
                    [ -f .env ] && cp .env .env.bak || true

                    # 3. Create new .env
                    cat <<ENV > .env
                    ACR_URL=${env.ACR_URL}
                    IMAGE_NAME=${env.IMAGE_NAME}
                    IMAGE_TAG=${env.DOCKER_TAG}
                    RABBIT_PASS=${RABBIT_PASS}
                    HOST_PORT=${targetPort}
                    ENV

                    # 4. Deployment
                    sudo docker compose pull
                    sudo docker compose up -d --force-recreate

                    # 5. SMART HEALTH CHECK WITH ROLLBACK
                    echo "Checking app health..."
                    sleep 15
                    if ! curl -f http://localhost:${targetPort}/; then
                        echo "❌ Health check failed! Rolling back to previous version..."
                        if [ -f .env.bak ]; then
                            mv .env.bak .env
                            sudo docker compose up -d --force-recreate
                            echo "✅ Rollback complete."
                        fi
                        exit 1
                    fi
                    
                    # Purani images clean karna (Best Practice)
                    sudo docker image prune -f
EOF
            """
        }
    }
}
