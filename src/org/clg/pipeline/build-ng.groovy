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
      String bc = "${params.bldConfig}"
      
      stage('test') {
          def pipelineValue = "${bc}"  //declare the parameter in groovy and use it in shellscript
          sh '''
             echo '''+pipelineValue+'''
	 
             '''
        }
	    
      stage('Checkout') {
        try {
	  echo 'about to checkout and print namespace'
		
	  echo "Hello from Angular-project ${openshift.project()} build-config is ${bc} in cluster ${openshift.cluster()} - param is ${params}"

           sh '''
            
	       echo "echo test build from shell in checkout stage build config " + "-- " + bc
	      oc start-build bc --from-dir=dist --follow
            '''
		
          git url: "${params.gitUrl}", branch: "${params.gitBranch}", credentialsId: "${namespace}-${params.gitSecret}"
        } catch (Exception e) {
	  echo 'retrying with sslverify turned off'
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
	    //    oc start-build angular-example-rhel --from-dir=dist --follow
	       String bc2 = "${openshift.project()}-${params.gitBranch}"
	        echo "Hello BuildImage ${openshift.project()} build-config is ${bc} in cluster ${openshift.cluster()} - param is ${params}"
	       echo "building from saroj3k-okd-pipeline bc ${bc2}"
            sh '''
              mkdir dist/nginx-cfg
              cp nginx/status.conf dist/nginx-cfg
	       echo "Hello buildImage within shellscript ${params.bldConfig}"
	      oc start-build "${params.bldConfig}" --from-dir=dist --follow
            '''
	    //kick off the build using Openshift raw command
	    //openshift.raw("start-build ${namespace}-${params.gitBranch} --from-dir=dist --follow")

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
