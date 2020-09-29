import com.vpogu.Constants;

def call(body) {

  def pipelineParams = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = pipelineParams
  body()

  def imageName = "${pipelineParams.imageName}"
  def namespace = "${pipelineParams.namespace}"
  def deploymentName = "${pipelineParams.deploymentName}"
  def subFolder = "${pipelineParams.subFolder}" == null ?"${pipelineParams.subFolder}" : "."

  pipeline {
    agent {
      kubernetes {
        label "pipeline-${UUID.randomUUID().toString()}"
        yaml libraryResource("templates/pipeline.yaml")
      }
    }
    options {
        buildDiscarder(logRotator(daysToKeepStr: "7", numToKeepStr: ""))
        disableConcurrentBuilds()
        timeout(time: 1, unit: "HOURS")
    }
    stages {
      stage("build") {
        steps{
          container("docker") {
              sh "docker run --rm --privileged multiarch/qemu-user-static --reset -p yes"
              sh "docker run --rm --privileged docker/binfmt:66f9012c56a8316f9244ffd7622d7c21c1f6f28d"
              sh "cd `pwd` && DOCKER_CLI_EXPERIMENTAL=enabled DOCKER_BUILDKIT=1 docker build --platform linux/arm64 -t docker.io/vikaspogu/${imageName} ${subFolder}"
          }
        }
      }
      stage("push") {
        steps{
          container("docker") {
            sh "DOCKER_CLI_EXPERIMENTAL=enabled DOCKER_BUILDKIT=1 docker push docker.io/vikaspogu/${imageName}"
          }
        }
      }
      stage("deployment"){
        steps{
          container("kubectl"){
            sh "kubectl rollout restart deployment/${deploymentName} -n ${namespace}"
          }
        }
      }
    }
    post {
        always {
            cleanWs()
        }
        success {
            slackSend (color: 'good', message: "🚀 Build Success: ${env.JOB_NAME} ${env.BUILD_DISPLAY_NAME}")
        }
        failure {
            slackSend (color: 'danger', message: "🔥 Build Failure: ${env.JOB_NAME} ${env.BUILD_DISPLAY_NAME}")
        }
    }
  }
}
