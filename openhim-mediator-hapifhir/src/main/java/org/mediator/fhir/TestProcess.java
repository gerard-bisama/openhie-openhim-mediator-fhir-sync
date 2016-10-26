package org.mediator.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.IGenericClient;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Practitioner;


import java.net.URI;
import java.net.URL;
import java.util.Date;

/**
 * Created by server-hit on 10/22/16.
 */
public class TestProcess {
    public static void main(String[] args)
    {
        MediatorFhirConfig mediatorConfiguration=new MediatorFhirConfig();
        try {
            UpdatePractitioner();
            int u=0;

            /*
            String dateSource="2016-10-02T08:12:43";
            String dateSource2="2016-10-02 08:12:43";
            int index=dateSource.indexOf("S");
            dateSource=dateSource.trim();
            dateSource=dateSource.replaceAll("T"," ");

            SimpleDateFormat simpleDateFormat=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date convertedDate=simpleDateFormat.parse(dateSource);
            Date convertedDate2=simpleDateFormat.parse(dateSource2);
            int res=convertedDate.compareTo(convertedDate2);
            System.out.println(convertedDate.toString());

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss");
            Date currentDate = new Date();
            String resFormat=dateFormat.format(currentDate);
            System.out.println(resFormat);
            */


            String builtRequestPath="";
            String requestgetPath="/practitioner/2016-10-14T08:09:23";
            Date syncDate=FhirMediatorUtilities.getDateFromRequestPath(requestgetPath);
            if(syncDate == null)
            {
                throw new Exception("Invalide date format in the request");
            }
            String stringLastSyncDate=mediatorConfiguration.getLastSyncDate();
            Date lastSyncDate=null;
            if(stringLastSyncDate.equals("0"))
            {
                //use the min threshold since no sync has been successful
                lastSyncDate=FhirMediatorUtilities.getDateFromRequestStringDate(mediatorConfiguration.getMinTresholdSyncDate());
                stringLastSyncDate=mediatorConfiguration.getMinTresholdSyncDate();
            }
            else
            {
                lastSyncDate=FhirMediatorUtilities.getDateFromRequestStringDate(stringLastSyncDate);
            }

            int compareDateRes=syncDate.compareTo(lastSyncDate);
            if(compareDateRes<=0)
            {
                System.out.println("The last sync date is greater or equal the the actual date! Data already synchronized");
                return;
            }
            System.out.println("Building request...");
            builtRequestPath=FhirMediatorUtilities.BuildSearchPathRequestToFhirServerHapi(requestgetPath,
                    stringLastSyncDate);
            System.out.println("FHIR API Request"+builtRequestPath);
            if(builtRequestPath=="")
            {
                throw new Exception("Fail to build the request to the source server repository");
            }
            String ServerApp=mediatorConfiguration.getServerSourceAppName().equals("null")?null:mediatorConfiguration.getServerSourceAppName();

            String baseServerRepoURI=FhirMediatorUtilities.buidServerRepoBaseUri(
                    mediatorConfiguration.getSourceServerScheme(),
                    mediatorConfiguration.getSourceServerURI(),
                    mediatorConfiguration.getSourceServerPort(),
                    ServerApp,
                    mediatorConfiguration.getSourceServerFhirDataModel()
            );
            String uriRepServer=baseServerRepoURI+builtRequestPath;
            URL url=new URL(uriRepServer);
            URI uri= new URI(url.getProtocol(),url.getUserInfo(),url.getHost(),url.getPort(),url.getPath(),
                    url.getQuery(),url.getRef());
            System.out.println(uri.toURL().toString());

            System.out.println(baseServerRepoURI);

        } catch (Exception e) {
           System.out.print(e.getMessage());
        }
    }
    private static void UpdatePractitioner()
    {
        //Create a factory
        String serverBaseUrl = "http://localhost:8082/hapi-fhir-jpaserver-local/baseDstu3";

        //String serverBaseUrl="http://192.168.1.15:8080/hapi-fhir-jpaserver";
        FhirContext ctx =  FhirContext.forDstu3();
        IGenericClient client = ctx.newRestfulGenericClient(serverBaseUrl);

        Practitioner oPractitioner=new Practitioner();
        //Add a collection of identifier
        oPractitioner.addIdentifier().setSystem("nid").setValue("1004-20");
        oPractitioner.addIdentifier().setSystem("mrn").setValue("LN12733");
        oPractitioner.addIdentifier().setSystem("http://example.com/fhir/localsystems/practitioner-id").setValue("ExamplePractitioner2");

        //Add names
        oPractitioner.addName().addFamily("Ethan").addGiven("Mutshipayi ya kalota").addPrefix("Pr");
        //oPractitioner.setId("Practitioner/103");
        MethodOutcome outcome;
        Bundle resourceBundle=new Bundle();
        resourceBundle.setType(Bundle.BundleType.TRANSACTION);
        Bundle.BundleEntryComponent oBundleEntry= new Bundle.BundleEntryComponent().setResource(oPractitioner);
        String searchPattern="Practitioner?";
        searchPattern+="identifier="+"http://example.com/fhir/localsystems/practitioner-id"+"|"+"ExamplePractitioner2";
        oBundleEntry.getRequest().setUrl(searchPattern).setMethod(Bundle.HTTPVerb.PUT);
        resourceBundle.addEntry(oBundleEntry);
        Bundle respOutcome=client.transaction().withBundle(resourceBundle).execute();
        /*
        outcome=client.update().resource(oPractitioner).
                conditional().where(oPractitioner.IDENTIFIER.
                exactly().
                systemAndIdentifier("mrn","LN12733")).execute();
         */
        //MethodOutcome outcome=client.update().resource(oPractitioner).execute();
        System.out.println(0);

    }

}
