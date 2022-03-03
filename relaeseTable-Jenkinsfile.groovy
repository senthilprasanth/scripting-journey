//Global Variables 
def releaseName = "${release}".trim()
def filterReleaseName = "/${releaseName}"
def jenkinsJobsList
def releaseList
def releaseJobsList
def releaseMap

// method to get all Jenkins jobs list at a Jenkins instance.
def getAllJenkinsJobsList() {
   def jenkinsJobsList = Jenkins.instance.getAllItems(org.jenkinsci.plugins.workflow.job.WorkflowJob)*.fullName
   return jenkinsJobsList
}

// method to get all Jenkins jobs list all available releases.
def getAllValidReleaseList(jenkinsJobsList) {
    def releaseList = []
    jenkinsJobsList.each {
        def releaseValue = it.tokenize("/")[1]
        releaseList.add(releaseValue)
    }
    return releaseList.unique()
}

// method to get all Jenkins jobs list matching the input value.
def getReleaseJobList(jenkinsJobsList, filterReleaseName) {
    def releaseJobsList = []
    jenkinsJobsList.each {
        if (it.endsWith(filterReleaseName)) {
            releaseJobsList.add(it)
        }
    }
    return releaseJobsList
}

// method to get last succsessful build number with all related info in a config map.
def getLastSuccessfulBuildNumber(releaseJobsList) {
    def releaseMap = [:]
    releaseJobsList.each {
        def splittedValue = it.split("/")
        releaseMap[splittedValue[0].trim()] = splittedValue[1].trim()
    }
    releaseMap.each { mapKey, mapValue ->
        try {
            def buildNumber = Jenkins.instance.getItem("${mapKey}").getItem("${mapValue}").lastSuccessfulBuild.number
            releaseMap[mapKey] = [mapValue, buildNumber]
        }
        catch (Exception e) {
            releaseMap[mapKey] = [mapValue, "NA"]
        }
    }
    return releaseMap
}

// method to fetch the build version string from Jenkins build log.
def getBuildVersionFromBuildLog(releaseMap, releaseName) {
    releaseMap.each { mapKey, mapValue ->
        if (releaseMap["${mapKey}"][1] != "NA") {
            def pipelineJobName = "${mapKey}/${releaseName}"
            def lastSuccessfulBuildNumber = releaseMap["${mapKey}"][1]
            def jobBuildPath = Jenkins.getInstance().getItemByFullName("${pipelineJobName}").getBuildByNumber(Integer.parseInt("${lastSuccessfulBuildNumber}")).logFile
            def buildString = (["grep", "imageVersion", "${jobBuildPath}"].execute().text.trim())
            def buildVersion = buildString.tokenize(":")[1]
            mapValue.add(buildVersion)
        }
        else {
            mapValue.add("Not Available")
        }
    }
    return releaseMap
}

// method to compile and create a HTML table.
def createHtmlTableFile(buildVersionReleaseMap, releaseName){
	sh "touch Microservice-Version-Details-${releaseName}-Report-${BUILD_NUMBER}.txt"
	def readContent = readFile "Microservice-Version-Details-${releaseName}-Report-${BUILD_NUMBER}.txt"
	writeFile file: "Microservice-Version-Details-${releaseName}-Report-${BUILD_NUMBER}.txt", text: readContent+"\r\n<html><br><br><body style='text-align:center'><table border=1 align='center'><style>table, th, td {border: 2px solid whitesmoke;  border-style: groove; border-collapse: collapse;}th, td {background-color: #E3F6E5;}html * {font-family: Cambria, Cochin, Georgia, Times, 'Times New Roman', serif;}</style><h1 align='center'> Microservice Build version Table for ${releaseName} </h1> <tr><th>Microservice</th><th>Version</th></tr><tr>\r\n"
    buildVersionReleaseMap.each { mapKey, mapValue ->
        readContent = readFile "Microservice-Version-Details-${releaseName}-Report-${BUILD_NUMBER}.txt"
        writeFile file: "Microservice-Version-Details-${releaseName}-Report-${BUILD_NUMBER}.txt", text: readContent+"<td>${mapKey}</td><td>${mapValue[2]}</td></tr>\n"
    }
    readContent = readFile "Microservice-Version-Details-${releaseName}-Report-${BUILD_NUMBER}.txt"
    writeFile file: "Microservice-Version-Details-${releaseName}-Report-${BUILD_NUMBER}.txt", text: readContent+"</table></body></html>\r\n"
    sh "cp Microservice-Version-Details-${releaseName}-Report-${BUILD_NUMBER}.txt Microservice-Version-Details-${releaseName}-Report-${BUILD_NUMBER}.html"
}

pipeline {
    agent any 
    stages {
        stage('Validate Input Release Name') { 
            steps {

                // Cleaning Jenkins workspace.
                cleanWs()
                script {
                    
                    // Get all Jenkins job lists.
                    echo "Fetching all Jenkins Jobs List"
                    jenkinsJobsList = getAllJenkinsJobsList()
                    echo "Complete Jenkins Jobs List here :- ${jenkinsJobsList}"
                    
                    // Get all Release name list available in Jenkins.
                    echo "Fetching all Valid Releases List from Jenkins"
                    releaseList = getAllValidReleaseList(jenkinsJobsList)
                    echo "Complete Valid Releases List here - ${releaseList}"

                    // Check input release against valid Release name list.
                    // Continue if it is VALID. Fail it is INVALID.
                    echo "User entered Release Name - ${releaseName}"
                    if (releaseList.contains(releaseName)) {
                        echo "Release Name ${releaseName} is VALID. \n Proceeding to next stage."
                        }
                    else {
                        echo "Release Name ${releaseName} is Invalid"
                        error("INVALID RELEASE NAME ${releaseName}- This pipeline stops here! \n Please check your Input Value.")
                    }
                }
            }
        }
        stage('Evaluate Build Version') {
           steps {
               script {

                   // Get all Jenkins Jobs List matching input Release Value.
                   echo "Fetching all Jenkins Jobs List matching the input Release Value."
                   releaseJobsList = getReleaseJobList(jenkinsJobsList, filterReleaseName)
                   echo " Complete Jenkins Jobs List matching the input Release Value - ${releaseJobsList}"

                   // Building a Release Map with last successfull build number.
                   echo "Fecthing last successful Build Numbers for each Jenkins Jobs matching Release Value."
                   releaseMap = getLastSuccessfulBuildNumber(releaseJobsList)
                   buildVersionReleaseMap = getBuildVersionFromBuildLog(releaseMap, releaseName)
                   echo "Complete Release Map here - ${buildVersionReleaseMap}"
               }
           }
        }
        stage('Create Microservice Build Version Table') {
            steps {
                script {

                    //Create HTML content.
                    echo "Creating HTML table for Release ${releaseName}"
                    createHtmlTableFile(buildVersionReleaseMap, releaseName)
                    echo "HTML table created successfully."
                }
            }
        }
        stage('Archive HTML Reports') {
            steps {
                archiveArtifacts artifacts: "Microservice-Version-Details-${releaseName}-Report-${BUILD_NUMBER}.*"
            }
        }
        stage('Publish HTML Report') {
            steps {
                publishHTML([allowMissing: false, alwaysLinkToLastBuild: true, keepAll: false, reportDir: '.', reportFiles: '*.html', reportName: 'Microservice Buildversion Report', reportTitles: ''])
            }
        }

    }
}