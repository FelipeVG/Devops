pipeline
{
	agent any

	environment
	{
		// Project configurations
		PROJECT_NAME = "Nissan.Cocos";
		CSPROJECT="Nissan.Cocos.Web"
        VERSIONARTIFACT ="Nissan.Cocos.Web${currentBuild.number}.zip" 
        
		CONFIGURATION = "Release";
		
		
		//Git Configurations
		BRANCH = "develop";
		GIT_TOKEN = "706e40ca-8c63-4b10-a61f-dce3416cedf0";
		GIT_SOURCE = "http://alm.axity.com/NISSAN-AMS/_git/COCOS";
		
		//FTP
		FTPDIR = "192.168.0.118";
		FTPFOLDER="webapp";
		//
		HOSTINST="admin@192.168.0.118";
		HOSTFOLDER="C:\\Receive\\webapp";
		HOSTCONFIG = "C:\\Receive\\config\\Web.config";
		TEMPORALDATA= "C:\\TempUncompress";
		PUBLISHPATH= "C:\\Receive\\CocosWeb";
        VERSIONARTIFACTFOLDER= "Nissan.Cocos.Web${currentBuild.number}";
		
		
	}
	stages
	{
		
		stage ('Git-Scm')
		{
			steps
			{
				echo "Getting code updates from git repository"
				git branch: "${BRANCH}", credentialsId: "${GIT_TOKEN}", url: "${GIT_SOURCE}"
			}
		}
		
		stage ('Restore and Build')
		{
			steps
			{
				echo "Restoring Nuget Packages"
				bat "${NUGET} restore \"${WORKSPACE}\\${PROJECT_NAME}.sln\""
			
				echo "Deploy Proyect"
			    bat "\"${MSBUILDVS2019}\"	build.xml  /p:Configuration=${CONFIGURATION};SolutionName=${PROJECT_NAME};ProjectName=${CSPROJECT} /p:plataform=\"Any CPU\""
			    
			}
		}
		stage('Archive Artifact')
		{
		    steps
		    {
		        
		         powershell "Compress-Archive -Path \"${WORKSPACE}\\${CSPROJECT}\\${CONFIGURATION} \"${WORKSPACE}\\${VERSIONARTIFACT} "
			     archiveArtifacts artifacts: "${VERSIONARTIFACT}", fingerprint: true
			     
		        
		    }
		}
		stage('FTP Deploy QA Axity')
		{
		    steps
		    {
		     echo "FTP deploy"  
		     bat "ncftpput -R -v -u ftpUser -p Ab12345  ${FTPDIR}  ${FTPFOLDER} ${VERSIONARTIFACT}"
		     
		    }
		}
		stage('Remote Instrumentation QA Axity')
		{
		    steps
		    {
		        echo"Uncompress Files"
		       // bat "ssh ${HOSTINST} rmdir /s /q \"${TEMPORALDATA}\\${VERSIONARTIFACTFOLDER}\""
		        bat "ssh ${HOSTINST} powershell.exe -C \" Expand-Archive -LiteralPath \"${HOSTFOLDER}\\${VERSIONARTIFACT}\" -DestinationPath \"${TEMPORALDATA}\\${VERSIONARTIFACTFOLDER}\"\""
		        echo "Delete web.config"
		        bat "ssh ${HOSTINST} del \"${TEMPORALDATA}\\${VERSIONARTIFACTFOLDER}\\${CONFIGURATION}\\Web.config\""
		        echo "Copy hosting web.config"
		        bat "ssh ${HOSTINST} copy \"${HOSTCONFIG}\" \"${TEMPORALDATA}\\${VERSIONARTIFACTFOLDER}\\${CONFIGURATION}\\Web.config\""
		        echo "Copy Prepare Site"
		  
		        bat "ssh ${HOSTINST} rmdir /s /q ${PUBLISHPATH}"
		        
		        bat "ssh ${HOSTINST} xcopy /E /I \"${TEMPORALDATA}\\${VERSIONARTIFACTFOLDER}\\${CONFIGURATION}\" ${PUBLISHPATH}\\ "
		    }
		}
		
		
	}
	post
	{
		always
		{
		
			SlackMsg(currentBuild.currentResult)
			//deleteDir()
		}
		success
		{
			echo "Pipieline executed correctly!"
		}
		failure
		{
			echo "Pipieline has failed!"
			
		}
	}
}

def SlackMsg(String buildResult)
{
	String message = "Compilation result ${env.BUILD_DISPLAY_NAME} for job  \"${env.JOB_NAME}\"  is " + buildResult +  ". For more details got to ${env.JOB_URL}"

	if(buildResult == "SUCCESS")
	{
		slackSend color: "good", message: message
	}
	else if(buildResult == "UNSTABLE")
	{
		slackSend color: "warning", message: message
	}
	else
	{
		slackSend color: "danger", message: message
	}
}