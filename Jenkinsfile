// ============================================================
// Jenkinsfile – SRE grade user-metadata-service (local Docker Jenkins)
// ============================================================
pipeline {
    agent any

    environment {
        AWS_REGION     = 'us-east-2'
        AWS_ACCOUNT_ID = credentials('aws-account-id')
        ECR_BASE       = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
        IMAGE_TAG      = "${env.GIT_COMMIT ? env.GIT_COMMIT.take(7) : 'latest'}-${env.BUILD_NUMBER}"
    }

    options {
        timeout(time: 45, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    stages {

        // ── 1. Checkout ───────────────────────────────────────────────────
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.GIT_AUTHOR  = sh(returnStdout: true, script: "git log -1 --pretty=%an").trim()
                    env.GIT_MESSAGE = sh(returnStdout: true, script: "git log -1 --pretty=%s").trim()
                }
                echo "Building: ${env.GIT_MESSAGE} by ${env.GIT_AUTHOR} | tag=${env.IMAGE_TAG}"
            }
        }

        // ── 2. Test: user-metadata-service ────────────────────────────────
        stage('Test: user-metadata-service') {
            steps {
                dir('user-metadata-service') {
                    sh 'mvn test --batch-mode'
                }
            }
            post {
                always {
                    junit allowEmptyResults: true,
                          testResults: 'user-metadata-service/target/surefire-reports/*.xml'
                }
            }
        }

        // ── 3. Test: deployment-portal ────────────────────────────────────
        stage('Test: deployment-portal') {
            steps {
                dir('deployment-portal') {
                    sh 'mvn test --batch-mode'
                }
            }
            post {
                always {
                    junit allowEmptyResults: true,
                          testResults: 'deployment-portal/target/surefire-reports/*.xml'
                }
            }
        }

        // ── 4. Build: user-metadata-service ───────────────────────────────
        stage('Build: user-metadata-service') {
            steps {
                dir('user-metadata-service') {
                    sh 'mvn package -DskipTests --batch-mode'
                }
            }
        }

        // ── 5. Build: deployment-portal ───────────────────────────────────
        stage('Build: deployment-portal') {
            steps {
                dir('deployment-portal') {
                    sh 'mvn package -DskipTests --batch-mode'
                }
            }
        }

        // ── 6. Docker Build & Push to ECR ─────────────────────────────────
        stage('Docker Build & Push') {
            steps {
                withCredentials([
                    [
                        $class:            'AmazonWebServicesCredentialsBinding',
                        credentialsId:     'aws-credentials',
                        accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                        secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                    ]
                ]) {
                    sh """
                        aws ecr get-login-password --region ${AWS_REGION} \
                          | docker login --username AWS --password-stdin ${ECR_BASE}
                    """

                    // user-metadata-service
                    sh """
                        docker build \
                            -t ${ECR_BASE}/user-metadata-service:${IMAGE_TAG} \
                            -t ${ECR_BASE}/user-metadata-service:latest \
                            ./user-metadata-service
                        docker push ${ECR_BASE}/user-metadata-service:${IMAGE_TAG}
                        docker push ${ECR_BASE}/user-metadata-service:latest
                    """

                    // deployment-portal
                    sh """
                        docker build \
                            -t ${ECR_BASE}/deployment-portal:${IMAGE_TAG} \
                            -t ${ECR_BASE}/deployment-portal:latest \
                            ./deployment-portal
                        docker push ${ECR_BASE}/deployment-portal:${IMAGE_TAG}
                        docker push ${ECR_BASE}/deployment-portal:latest
                    """
                }
            }
        }
    }

    post {
        success {
            echo "Build SUCCESS — images pushed with tag: ${env.IMAGE_TAG}"
        }
        failure {
            echo "Build FAILED — check console output above"
        }
        always {
            cleanWs()
        }
    }
}
