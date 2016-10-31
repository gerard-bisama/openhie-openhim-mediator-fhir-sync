# openhie-openhim-mediator-fhir-sync
The mediator synchronizes data between two fhir repository servers.
It pulls Practitioner resources from a first server (Public Test Serve : http://fhirtest.uhn.ca/ or any type of Restful FHIR Server), validate them and push them it the local FHIR JPA Server that stores   resource on the local database (MySQL). The data model format is based on STU3.
Practitioner covers all individuals who are engaged in the healthcare process and healthcare-related services as part of their formal responsibilities and this Resource is used for attribution of activities and responsibilities to these individuals.

#Getting started guide
#Configuration
1. The first configuration concerns the mediator sync service. Ones need to determine the source server and the target server parameters.
```sh
useradd -d /home/fhirmediator -m fhirmediator -s /bin/bash
cd /home/ fhirmediator
mkdir config
cp  /thelocationofyoursourcecode/fhirmediator.properties  /home/mediator/config/
cp  /thelocationofyoursourcecode/fhirmediator_sync.properties  /home/mediator/config/
sudo usermod -a -G youruser fhirmediator
sudo chown -R fhirmediator:yourusergroup /home/fhirmediator/config/
 ```

Open the hirmediator.properties and change the value according to your configuration (baseUrl, servers port,minimun limit date for your first synchronization)

The mediator will automatically install 1 channel (mainfhir-mediator-channel) to control the synchronisation, you must also configure these channels to suit your needs. In later versions of the OpenHIM (after 1.5.1) these will not be installed automatically, you will need to manually install these channels by navigating to Mediators > openhim-mediator-hapifhir Mediator and clikcing the little + button next to the channel listed under the 'Default channels' section.

2. To make the JPA server working you need to create a new mysql data base the change the configuration files of JPA local server
Open the file FhirServerConfigDstu3.java, locate the code the public DataSource dataSource(), then  replace the lines below to the corresponding values.

```sh
retVal.setUrl("jdbc:mysql://localhost:3306/hapifhir_stu3");
		retVal.setUsername("root");
		retVal.setPassword("xxxxxx");
```
The JPA server will run on the port 8082 but if Ones needs to change this value. Open the pom.xml file and locate the tab <httpConnector> in <configuration> then change the port number.
#Installation

1. To install the mediator, download the source files from the openhie-openhim-mediator-fhir-sync repository. Enter in the openhim-mediator-hapifhir and generate the jar file.

```sh
cd  /thelocationofyoursourcecode/ openhim-mediator-hapifhir
mvn install
java -jar target/fhir-mediator-0.1.0-jar-with-dependencies.jar
```

2. to install the JPA Fhir local server

```sh
cd /thelocationofyoursourcecode /hapi-fhir-jpaserver-local
mvn install
mvn jetty:run
```
Then, the Fhir local server can be accessed on http://localhost:8082/hapi-fhir-jpaserver-local (localhost: can be replaced by a public IP address)

#Launch the synchronization
To run the synchronization of data, run the command
```sh
 curl -k -u clientuser:clientgroup https://localhost:5000/synchronize/2016-10-12T01:01:01
 ```

Where clientuser and clientgroup are the one that you have configure for your channel and the value « 2016-10-12T01:01:01 » is the synchronization date from the last sync date.
