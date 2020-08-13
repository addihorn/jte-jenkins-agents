/* all resources will be pulled from the original scm-repo

void call() { 
	podTemplate(
		containers : [
			containerTemplate(
				name: 'jnlp',
				image: 'quay.io/openshift/origin-jenkins-agent-base',
				ttyEnabled: true
			)
		]
	) 
	{
		node(POD_LABEL) {
			checkout scm
			// figure out how to set env.GIT_* Variables
			stage ('OpenShift Build') {			
				echo "Hello From OpenShift Build"
				echo "GIT_COMMIT: ${env.GIT_COMMIT}"
				echo "GIT_URL: ${env.GIT_URL}"
				echo "GIT_BRANCH: ${env.GIT_BRANCH}"
				echo "JOB_NAME: ${env.JOB_NAME}"
				

				echo "GIT_COMMIT: ${env.GIT_COMMIT}"
				echo "GIT_URL: ${env.GIT_URL}"
				
				script {
					openshift.withCluster() {
						openshift.withProject() {
							//def dockerFileContent = readFile(file:'Dockerfile')
							echo 'Creating Build Template and ImageStream'
							buildTemplate = 
									openshift.process(readFile(file:'buildManifests/buildConfig-Template.yaml'),
										"-p=GITURL=${env.GIT_URL}" ,
										"-p=COMMIT_ID=${env.GIT_COMMIT}",
										"-p=GIT_BRANCH=${env.GIT_BRANCH}",
										"-p=REPO_NAME=foobar")
							def appliedBuildArtifacts = openshift.apply(buildTemplate)	
							imageStreamRepo = appliedBuildArtifacts.narrow('is').object().status.dockerImageRepository
							
							def buildStatus
							
							echo 'Start Build'
							def buildProcess = appliedBuildArtifacts.narrow('bc').startBuild() 
							
							buildName = buildProcess.object().metadata.name
							
							// wait max of 20 minutes to finish build
							timeout(20) {
								waitUntil(initialRecurrencePeriod: 15000) {
									finished = true
									buildStatus = buildProcess.object().status.phase
									
									echo "Status of Build " + buildName + ": " + buildStatus
									
									// set finished if the build is in a terminated state (Completed, Failed, Cancelled)
									finished = finished && (buildStatus == "Complete" || buildStatus == "Failed" || buildStatus == "Cancelled")
									
									return finished
								}
							}

							if (buildStatus != "Complete") {
								unstable 'Build failed'
								echo 'Status Message: ' + buildProcess.object().status.message
								echo buildProcess.object().status.logSnippet
							}
						}
					}				
				}				
			}

		}
	}
}
