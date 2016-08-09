Wowza Piwik Tracker
================
This is a module for [![Wowza Streaming Engine v. 4.x] (https://www.wowza.com/)] that sends stats to [![Piwik] (https://piwik.org/)].

This project can be compiled with Eclipse with the Wowza Development kit installed.

## Build and runtime dependencies

	aopalliance-repackaged-2.4.0.jar
	hk2-api-2.4.0.jar
	hk2-locator-2.4.0.jar
	hk2-utils-2.4.0.jar
	javaee-api-7.0.jar
	javax.inject-2.4.0.jar
	javax.json-1.0.4.jar
	jersey-common-2.20.jar
	jersey-guava-2.20.jar
	piwik-java-tracker-1.1.jar



## Configuration
Edit the file `conf/Server.xml` and add the following in the appropriate sections:

                        <ServerListener>
                             <BaseClass>org.topix.wowza.piwik.PiwikListener</BaseClass>
                        </ServerListener>





                     <Property>
                             <Name>piwikListenerUrl</Name>
                             <Value>http://mypiwik.example.com</Value>
                             <Type>String</Type>
                     </Property>
                     <Property>
                             <Name>piwikListenerKey</Name>
                             <Value>my_secret_piwik_key</Value>
                             <Type>String</Type>
                     </Property>
                     <Property>
                             <Name>piwikListenerDefaultWebsiteId</Name>
                             <Value><id_of_the_website_in_piwik></Value>
                             <Type>String</Type>
                     </Property>
                     <Property>
                             <Name>piwikListenerDebug</Name>
                             <Value>true</Value>
                             <Type>Boolean</Type>
                     </Property>







