package org.clg.pipeline

def start(def params) {

  stage("Launch Node Agent") {
    node("nodejs") {
      git url: "${params.pipelineCodeGitUrl}", branch: "${params.pipelineCodeGitBranch}"
      build(params)
    }
  }
}

def build(def params) {
  openshift.withCluster() {
    openshift.withProject() {
      openshift.raw("label secret ${params.gitSecret} credential.sync.jenkins.openshift.io=true --overwrite")
      def namespace = openshift.project()
      stage('Checkout') {
        try {
          git url: "${params.gitUrl}", branch: "${params.gitBranch}", credentialsId: "${namespace}-${params.gitSecret}"
        } catch (Exception e) {
          sh "git config http.sslVerify false"
          git url: "${params.gitUrl}", branch: "${params.gitBranch}", credentialsId: "${namespace}-${params.gitSecret}"
        }
      } //stage checkout

      stage ('install modules'){
          sh '''
            npm install --verbose -d
            npm install --save classlist.js
          '''
        } //stage install

       //defaults to prod build
       stage ('build') {
            sh '$(npm bin)/ng build --prod --build-optimizer'
          } //stage build

          //build image
       stage ('build image') {
            sh '''
              mkdir dist/nginx-cfg
              cp nginx/status.conf dist/nginx-cfg
              oc start-build angular-5-example-rhel --from-dir=dist --follow
            '''
          } //stage build image	    
      } //openshift withProject
    } //openshift withCluster
  } //build

def readYamlFile(def filePath, String errMessage) {
	try {
		def data = readYaml file: filePath
    data?.metadata?.remove('namespace')
	  if(data?.metadata?.labels == null && data?.metadata != null) {
		  data?.metadata?.labels = [:]
	  }
		return data
	} catch(FileNotFoundException fnfe) {
		throw new RuntimeException(errMessage)
	}
}
return this
