package org.mediator.fhir;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import org.apache.http.HttpStatus;
import org.hl7.fhir.dstu3.model.Identifier;
import org.hl7.fhir.dstu3.model.Practitioner;
import org.openhim.mediator.engine.MediatorConfig;
import org.openhim.mediator.engine.messages.FinishRequest;
import org.openhim.mediator.engine.messages.MediatorHTTPRequest;
import org.openhim.mediator.engine.messages.MediatorHTTPResponse;

import java.text.SimpleDateFormat;
import java.util.*;

public class DefaultOrchestrator extends UntypedActor {
    LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    private final MediatorConfig config;
    private MediatorFhirConfig mediatorConfiguration;
    private SynDateConfigFile syncConfiguration;
    private MediatorHTTPRequest originalRequest;
    private String solvedPractitionerResponse;
    private List<String> listSolvedPractitionerResponse;
    private List<Practitioner> listOfValidePractitioner;
    private List<Practitioner> listOfPractitionerToUpdate;
    private List<Practitioner> listOfPractitionerToAdd;
    int nbrOfSearchRequestToWaitFor;
    String baseServerRepoURI;
    private List<Map<String,String>> listMapIdentifierUsedForUpdate;
    private List<Map<String,String>> listMapIdentifierdForUpdate;
    private Map<String,Practitioner> listIdentifiedPractitionerAndIdForUpdateSource;
    String resultOutPutHeader;
    String resultOutPutTail;
    String stringSyncDate;
    String logResult;
    enum operationTypeFlag {INSERT,UPDATE};

    FhirResourceValidator resourceBundle=null;


    public DefaultOrchestrator(MediatorConfig config) {
        this.config = config;
        this.mediatorConfiguration=new MediatorFhirConfig();
        this.syncConfiguration=new SynDateConfigFile();
        this.resourceBundle=new FhirResourceValidator();
        baseServerRepoURI="";
        resultOutPutHeader="{outputId:Synchronization result,";
        resultOutPutTail="}";
        solvedPractitionerResponse=null;
        listSolvedPractitionerResponse=new ArrayList<>();
        listOfValidePractitioner=new ArrayList<>();
        listOfPractitionerToUpdate=new ArrayList<>();
        listOfPractitionerToAdd=new ArrayList<>();
        nbrOfSearchRequestToWaitFor=0;
        listMapIdentifierUsedForUpdate=new ArrayList<>();
        listMapIdentifierdForUpdate=new ArrayList<>();
        listIdentifiedPractitionerAndIdForUpdateSource=new HashMap<>();
        SimpleDateFormat simpleDateFormat=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        logResult=simpleDateFormat.format(new Date()).toString()+"::";
    }

    private void queryFhirRepositoryResources(MediatorHTTPRequest request)
    {
        originalRequest = request;
        ActorSelection httpConnector = getContext().actorSelection(config.userPathFor("http-connector"));
        Map <String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");
        String resourceInformation="FHIR Practitioners resources";
        log.info("Querying the HAPI Test servers");


        String builtRequestPath="";
        String stringLastSyncDate="";
        stringSyncDate="";

        try
        {
            //Check date order. The syncDate must be > the last SyncDate
            Date syncDate=FhirMediatorUtilities.getDateFromRequestPath(request.getPath());
            if(syncDate == null)
            {
                throw new Exception("Invalide date format in the request");
            }
            stringSyncDate=FhirMediatorUtilities.getStringDateFromRequestPath(request.getPath());
            logResult+="[synchronization-info]:syndate="+stringSyncDate+",";
            stringLastSyncDate=this.syncConfiguration.getLastSyncDate();

            Date lastSyncDate=null;
            if(stringLastSyncDate.equals("0"))
            {
                //use the min threshold since no sync has been successful
                lastSyncDate=FhirMediatorUtilities.getDateFromRequestStringDate(this.mediatorConfiguration.getMinTresholdSyncDate());
                stringLastSyncDate=this.mediatorConfiguration.getMinTresholdSyncDate();
            }
            else
            {
                lastSyncDate=FhirMediatorUtilities.getDateFromRequestStringDate(stringLastSyncDate);
            }
            logResult+="lastSyncdate="+lastSyncDate+",";
            int compareDateRes=syncDate.compareTo(lastSyncDate);
            if(compareDateRes<=0)
            {
                log.warning("The last sync date is greater or equal the the actual date! Data already synchronized");
                logResult+="The last sync date is greater or equal the the actual date! Data already synchronized";
                FinishRequest fr = new FinishRequest(logResult, "text/plain", HttpStatus.SC_BAD_REQUEST);
                originalRequest.getRespondTo().tell(logResult, getSelf());
                return;
            }
            log.info("Building request...");
            builtRequestPath=FhirMediatorUtilities.BuildSearchPathRequestToFhirServerHapi(request.getPath(),
                    stringLastSyncDate);
            log.info("FHIR API Request"+builtRequestPath);
            if(builtRequestPath=="")
            {
                throw new Exception("Fail to build the request to the source server repository");
            }

        }
        catch (Exception exc)
        {
            log.error(exc.getMessage());
            logResult+="::error:"+exc.getMessage();
            return;
        }

        //builtRequestPath="/baseDstu3/Practitioner?_lastUpdated=>=2016-10-11T09:12:37&_lastUpdated=<=2016-10-13T09:12:45&_pretty=true";

        String ServerApp=mediatorConfiguration.getServerSourceAppName().equals("null")?null:mediatorConfiguration.getServerSourceAppName();

        baseServerRepoURI=FhirMediatorUtilities.buidServerRepoBaseUri(
                this.mediatorConfiguration.getSourceServerScheme(),
                this.mediatorConfiguration.getSourceServerURI(),
                this.mediatorConfiguration.getSourceServerPort(),
                ServerApp,
                this.mediatorConfiguration.getSourceServerFhirDataModel()
        );
        //String uriRepServer=baseServerRepoURI+"/Practitioner?_lastUpdated=%3E=2016-10-11T09:12:37&_lastUpdated=%3C=2016-10-13T09:12:45&_pretty=true";
        String uriRepServer=baseServerRepoURI+builtRequestPath;

        String encodedUriSourceServer=FhirMediatorUtilities.encodeUrlToHttpFormat(uriRepServer);
        MediatorHTTPRequest serviceRequest = new MediatorHTTPRequest(
                request.getRequestHandler(),
                getSelf(),
                resourceInformation,
                "GET",
                encodedUriSourceServer,
                null,
                headers,
                null);

        resultOutPutHeader+="lastSyncDate:"+stringLastSyncDate+","+"synDate:"+stringSyncDate+",";
        httpConnector.tell(serviceRequest, getSelf());

    }

    private void processFhirRepositoryServiceResponse(MediatorHTTPResponse response) {
        log.info("Received response Fhir repository Server");
        //originalRequest.getRespondTo().tell(response.toFinishRequest(), getSelf());
        //Perform the resource validation from the response
        try
        {
            if (response.getStatusCode() == HttpStatus.SC_OK) {
                StringBuilder strResponse=new StringBuilder();
                //Copy the response Char by char to avoid the string size limitation issues
                strResponse.append(response.getBody());
                resourceBundle.set_jsonResource(strResponse);
                String pathOfErrorResource=this.mediatorConfiguration.getPathForResourceWithError();
                //boolean resProcessing= resourceBundle.processBundleJsonResource(pathOfErrorResource);
                boolean resProcessing= resourceBundle.processBundleJsonResource(pathOfErrorResource,baseServerRepoURI);
                if(!resProcessing)
                {
                    throw new Exception("Failed to process the fhir synchronization request");
                }
                int nbrRetreivedPractitioner=resourceBundle.getListOfPractitioners().size();
                int nbrValidPractitioner=resourceBundle.getListOfValidePractitioners().size();
                int nbreInvalidPractitioner=resourceBundle.getListOfInvalidePractitioners().size();
                resultOutPutHeader+="totalPractitionerFound:"+nbrRetreivedPractitioner+","+
                        "totalNbreOfValidPractitioner:"+nbrValidPractitioner+","+
                        "totalNbreOfInvalidPractitioner:"+nbreInvalidPractitioner+",";
                logResult+="::[request resource]:";
                logResult+="totalPractitionerFound="+nbrRetreivedPractitioner+","+
                        "totalNbreOfValidPractitioner="+nbrValidPractitioner+","+
                        "totalNbreOfInvalidPractitioner="+nbreInvalidPractitioner+","
                ;

                PractitionerOrchestratorActor.ResolvePractitonerRequest practitionerRequest =null;
                //Identify the number of Practitioner that are used to be searched
                //By identifier

                if(nbrValidPractitioner>0)
                {
                    List<Practitioner> tempListOfValidePractitioner= resourceBundle.getListOfValidePractitioners();
                    listOfValidePractitioner=FhirMediatorUtilities.removeDuplicateInTheList(tempListOfValidePractitioner);
                    //listOfValidePractitioner=resourceBundle.getListOfValidePractitioners();
                    for(Practitioner oPractitionerToIdentify:resourceBundle.getListOfValidePractitioners()) {
                        if(oPractitionerToIdentify.getIdentifier().size()>0)
                        {
                            nbrOfSearchRequestToWaitFor++;
                        }
                    }
                }
                for(Practitioner oPractitionerToIdentify:resourceBundle.getListOfValidePractitioners())
                {
                    //Check if the practitioner has an identifier
                    if(oPractitionerToIdentify.getIdentifier().size()>0)
                    {
                        //String id=oPractitionerToIdentify.getId();
                        List <Identifier> listOfIdentifier=oPractitionerToIdentify.getIdentifier();
                        for(Identifier oIdentifier :listOfIdentifier)
                        {
                            String idSystem=oIdentifier.getSystem();
                            String idValue=oIdentifier.getValue();
                            Map requestObject=new HashMap();
                            requestObject.put(idSystem,idValue);
                            listMapIdentifierUsedForUpdate.add(requestObject);
                            practitionerRequest=new PractitionerOrchestratorActor.ResolvePractitonerRequest(
                                    originalRequest.getRequestHandler(),
                                    getSelf(),
                                    requestObject
                                    );
                            ActorRef practitionerRequestOrchestrator=getContext().actorOf(
                                    Props.create(PractitionerOrchestratorActor.class,config));
                            practitionerRequestOrchestrator.tell(practitionerRequest,getSelf());
                            //break;
                        }

                    }

                }
                //System.out.print(listSolvedPractitionerResponse.size());
                /*

                String jsonResponse="{\"response\":\"OK\"," +
                        "\"Total practitioner found\":"+nbrRetreivedPractitioner+"," +
                        "\"Valid Practitioner\":"+nbrValidPractitioner+"," +
                        "\"Invalid Practitioner\":"+nbreInvalidPractitioner+"}";
                FinishRequest fr = new FinishRequest(jsonResponse, "application/json", HttpStatus.SC_OK);*/
                FinishRequest fr = new FinishRequest(logResult, "text/plain", HttpStatus.SC_OK);
                originalRequest.getRespondTo().tell(logResult, getSelf());
                //originalRequest.getRespondTo().tell(fr, getSelf());

            }
            else {
                logResult+="::warning:"+response.toFinishRequest().toString();
                //originalRequest.getRespondTo().tell(response.toFinishRequest(), getSelf());
                originalRequest.getRespondTo().tell(logResult, getSelf());

            }

        }
        catch (Exception exc)
        {
            log.error(exc.getMessage());
            logResult+="::error:"+exc.getMessage();
            originalRequest.getRespondTo().tell(logResult, getSelf());
            return;
        }

    }
    private void finalizeRequest(String practitionerResponse) {


        if(practitionerResponse==null && this.nbrOfSearchRequestToWaitFor>0)
        {
            return;
        }
        else if(practitionerResponse!=null && this.nbrOfSearchRequestToWaitFor>0)
        {
            listSolvedPractitionerResponse.add(practitionerResponse);
        }
        if(listSolvedPractitionerResponse.size()<nbrOfSearchRequestToWaitFor && this.nbrOfSearchRequestToWaitFor>0)
        {
            return;
        }
        else
        {
            FinishRequest fr =null;
            try
            {
                //finish
                System.out.println(listSolvedPractitionerResponse.size());
                System.out.println(nbrOfSearchRequestToWaitFor);
                //Identify List of Practitioner to Update
                //this.listOfPractitionerToUpdate=IdentifyPractitionerToUpdate(listSolvedPractitionerResponse);
                identifyMapCouplePractitionerAndIdToUpdate(listSolvedPractitionerResponse);
                this.IdentifyPractitionerToAdd();
                //from the original list of Practitioner found, extract the rest of Practitioner to add
                String ServerApp="";
                String baseServerRepoURI="";
                ServerApp=mediatorConfiguration.getServerTargetAppName().equals("null")?null:mediatorConfiguration.getServerTargetAppName();
                baseServerRepoURI=FhirMediatorUtilities.buidServerRepoBaseUri(
                        this.mediatorConfiguration.getServerTargetscheme(),
                        this.mediatorConfiguration.getServerTargetURI(),
                        this.mediatorConfiguration.getServerTargetPort(),
                        ServerApp,
                        this.mediatorConfiguration.getServerTargetFhirDataModel()
                );
                String resultInsertion=null;
                resultOutPutHeader+="res:";
                if(listOfPractitionerToAdd.size()>0){
                    resultInsertion =FhirResourceProcessor.createPractitioner(resourceBundle.getContext(),
                            this.listOfPractitionerToAdd,
                            baseServerRepoURI);
                }
                if(resultInsertion!=null)
                {
                    resultOutPutHeader+="["+resultInsertion+",";
                }

                /*
                String  resultUpdate=FhirResourceProcessor.updatePractitionerInTransaction(resourceBundle.getContext(),
                        this.listOfPractitionerToUpdate,
                        baseServerRepoURI);
                 */
                String  resultUpdate=null;
                if(listIdentifiedPractitionerAndIdForUpdateSource.size()>0)
                {
                    resultUpdate=FhirResourceProcessor.updatePractitionerInTransaction(resourceBundle.getContext(),
                            this.listIdentifiedPractitionerAndIdForUpdateSource,
                            baseServerRepoURI);
                }

                if(resultUpdate!=null && resultInsertion!=null)
                {
                    resultOutPutHeader+=resultUpdate+"";
                }
                else if ( resultUpdate!=null && resultInsertion==null)
                {
                    resultOutPutHeader+="["+resultUpdate+"";
                }
                else if ( resultUpdate==null && resultInsertion==null)
                {
                    resultOutPutHeader+="[]";
                }
                else if( resultUpdate==null && resultInsertion!=null)
                {
                    resultOutPutHeader+="]";
                }
                resultOutPutHeader+=resultOutPutTail;

                System.out.print(0);
                logResult+=resultOutPutHeader;
                SimpleDateFormat simpleDateFormat=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                logResult+="::"+simpleDateFormat.format(new Date()).toString()+":";
                logResult+="Operation completed!";

                this.syncConfiguration.setLastSyncDate(this.stringSyncDate);
                //fr = new FinishRequest(resultOutPutHeader, "application/json", HttpStatus.SC_OK);
                fr = new FinishRequest(logResult, "text/plain", HttpStatus.SC_OK);
            }
            catch (Exception exc)
            {
                log.error(exc.getMessage());
                String errorMessage="{error:"+exc.getMessage()+"}";
                logResult+="::error:"+exc.getMessage();
                fr = new FinishRequest(logResult, "text/plain", HttpStatus.SC_OK);
            }
            originalRequest.getRespondTo().tell(fr, getSelf());
        }

        //return;

    }
    List<Practitioner> IdentifyPractitionerToUpdate(List<String> bundleSearchResultSet)
    {
        List<Practitioner> ListIdentifiedForUpdateTarget=new ArrayList<>();
        List<Practitioner> listIdentifiedForUpdateSource=new ArrayList<>();
        try
        {
            for (String oBundleSearchResult:bundleSearchResultSet)
            {
                for (Practitioner oPractitioner :resourceBundle.extractPractitionerFromBundleString(oBundleSearchResult))
                {
                    if(ListIdentifiedForUpdateTarget.contains(oPractitioner))
                    {
                        continue;
                    }
                    ListIdentifiedForUpdateTarget.add(oPractitioner);
                }
                //ListIdentifiedForUpdateTarget.addAll(resourceBundle.extractPractitionerFromBundleString(oBundleSearchResult));
            }
        }
        catch (Exception exc)
        {
            log.error(exc.getMessage());
            return null;
        }
        //Search now the original correspondance based of Identifier
        for(Map<String,String> oMapIdentifier : this.listMapIdentifierUsedForUpdate)
        {
            //etract key and value as systemId and valueId
            //getValue
            String value=oMapIdentifier.values().toArray()[0].toString();
            String key=null;
            //getKey
            for(Object oKey :oMapIdentifier.keySet())
            {
                if(oMapIdentifier.get(oKey).equals(value))
                {
                    key=oKey.toString();
                    break;
                }

            }
            for (Practitioner oPractitioner:ListIdentifiedForUpdateTarget)
            {
                boolean foundCorrespondance=false;
                List<Identifier> oListIdentifiers=oPractitioner.getIdentifier();
                for (Identifier oIdentifier : oListIdentifiers)
                {
                    if(oIdentifier.getSystem().equals(key) && oIdentifier.getValue().equals(value))
                    {
                        //Map<String,String> mapIdentifierToUpdate=oMapIdentifier;
                        listMapIdentifierdForUpdate.add(oMapIdentifier);
                        foundCorrespondance=true;
                        break;

                    }

                }
                if(foundCorrespondance)
                {
                    break;
                }
            }

        }
        for(Map<String,String> oMapIdentifier : this.listMapIdentifierdForUpdate) {
            //etract key and value as systemId and valueId
            //getValue
            String value = oMapIdentifier.values().toArray()[0].toString();
            String key = null;
            //getKey
            for (Object oKey : oMapIdentifier.keySet()) {
                if (oMapIdentifier.get(oKey).equals(value)) {
                    key = oKey.toString();
                    break;
                }

            }
            for (Practitioner oPractitioner:this.listOfValidePractitioner)
            {
                boolean foundCorrespondance=false;
                List<Identifier> oListIdentifiers=oPractitioner.getIdentifier();
                for (Identifier oIdentifier : oListIdentifiers)
                {
                    if(oIdentifier.getSystem().equals(key) && oIdentifier.getValue().equals(value))
                    {
                        //Map<String,String> mapIdentifierToUpdate=oMapIdentifier;
                        //listMapIdentifierdForUpdate.add(oMapIdentifier);
                        if(!listIdentifiedForUpdateSource.contains(oPractitioner))
                        {
                            listIdentifiedForUpdateSource.add(oPractitioner);
                        }
                        foundCorrespondance=true;
                        break;

                    }

                }
                if(foundCorrespondance)
                {
                    break;
                }
            }


        }


        return listIdentifiedForUpdateSource;
    }
    void identifyMapCouplePractitionerAndIdToUpdate(List<String> bundleSearchResultSet)
    {
        List<Practitioner> ListIdentifiedForUpdateTarget=new ArrayList<>();
        List<Practitioner> listIdentifiedForUpdateSource=new ArrayList<>();
        try
        {
            for (String oBundleSearchResult:bundleSearchResultSet)
            {
                for (Practitioner oPractitioner :resourceBundle.extractPractitionerFromBundleString(oBundleSearchResult))
                {
                    if(ListIdentifiedForUpdateTarget.contains(oPractitioner))
                    {
                        continue;
                    }
                    ListIdentifiedForUpdateTarget.add(oPractitioner);
                }
                //ListIdentifiedForUpdateTarget.addAll(resourceBundle.extractPractitionerFromBundleString(oBundleSearchResult));
            }
        }
        catch (Exception exc)
        {
            log.error(exc.getMessage());
            return ;
        }
        //Search now the original correspondance based of Identifier
        for(Map<String,String> oMapIdentifier : this.listMapIdentifierUsedForUpdate)
        {
            //etract key and value as systemId and valueId
            //getValue
            String value=oMapIdentifier.values().toArray()[0].toString();
            String key=null;
            //getKey
            for(Object oKey :oMapIdentifier.keySet())
            {
                if(oMapIdentifier.get(oKey).equals(value))
                {
                    key=oKey.toString();
                    break;
                }

            }
            for (Practitioner oPractitioner:ListIdentifiedForUpdateTarget)
            {
                boolean foundCorrespondance=false;
                List<Identifier> oListIdentifiers=oPractitioner.getIdentifier();
                for (Identifier oIdentifier : oListIdentifiers)
                {
                    if(oIdentifier.getSystem().equals(key) && oIdentifier.getValue().equals(value))
                    {
                        //Map<String,String> mapIdentifierToUpdate=oMapIdentifier;
                        listMapIdentifierdForUpdate.add(oMapIdentifier);
                        foundCorrespondance=true;
                        break;

                    }

                }
                if(foundCorrespondance)
                {
                    break;
                }
            }

        }
        for(Map<String,String> oMapIdentifier : this.listMapIdentifierdForUpdate) {
            //etract key and value as systemId and valueId
            //getValue
            String value = oMapIdentifier.values().toArray()[0].toString();
            String key = null;
            //getKey
            for (Object oKey : oMapIdentifier.keySet()) {
                if (oMapIdentifier.get(oKey).equals(value)) {
                    key = oKey.toString();
                    break;
                }

            }
            for (Practitioner oPractitioner:this.listOfValidePractitioner)
            {
                boolean foundCorrespondance=false;
                List<Identifier> oListIdentifiers=oPractitioner.getIdentifier();
                for (Identifier oIdentifier : oListIdentifiers)
                {
                    if(oIdentifier.getSystem().equals(key) && oIdentifier.getValue().equals(value))
                    {
                        //Map<String,String> mapIdentifierToUpdate=oMapIdentifier;
                        //listMapIdentifierdForUpdate.add(oMapIdentifier);
                        if(!listIdentifiedForUpdateSource.contains(oPractitioner))
                        {
                            String updateSearchPattern=null;
                            if(key!=null)
                            {
                                 updateSearchPattern=key+"|"+value;
                            }
                            else
                            {
                                 updateSearchPattern=value;
                            }

                            this.listIdentifiedPractitionerAndIdForUpdateSource.put(updateSearchPattern,oPractitioner);

                            listIdentifiedForUpdateSource.add(oPractitioner);
                        }
                        foundCorrespondance=true;
                        break;


                    }

                }
                if(foundCorrespondance)
                {
                    break;
                }
            }


        }


        //return listIdentifiedForUpdateSource;
    }
    void IdentifyPractitionerToAdd()
    {
        //List<Practitioner> listOfPractitio
        try
        {
            for(Practitioner oPractitioner : this.listOfValidePractitioner)
            {
                boolean isToDiscard=false;
                //for (:listIdentifiedPractitionerAndIdForUpdateSource.)
                if(oPractitioner.getIdentifier().size()==0)
                {
                    this.listOfPractitionerToAdd.add(oPractitioner);
                    continue;
                }
                else
                {
                    List<Identifier> listOfIdentifier=oPractitioner.getIdentifier();
                    boolean isForUpdate=false;
                    for (Identifier oIdentifier:listOfIdentifier)
                    {
                        String identifierPattern=oIdentifier.getSystem()+"|"+oIdentifier.getValue();
                        //Check if the identifier if in the keySet
                        if(this.listIdentifiedPractitionerAndIdForUpdateSource.keySet().contains(identifierPattern))
                        {
                            isForUpdate=true;
                            break;
                        }
                    }
                    if(!isForUpdate)
                    {
                        this.listOfPractitionerToAdd.add(oPractitioner);
                    }
                    //continue;

                }
            }
        }
        catch (Exception exc)
        {
            log.error(exc.getMessage());
            //return null;
        }
    }

    @Override
    public void onReceive(Object msg) throws Exception {
        if (msg instanceof MediatorHTTPRequest) {
            queryFhirRepositoryResources((MediatorHTTPRequest) msg);
        } else if (msg instanceof MediatorHTTPResponse){
            processFhirRepositoryServiceResponse((MediatorHTTPResponse) msg);
            finalizeRequest(null);
        }
        else if (msg instanceof PractitionerOrchestratorActor.ResolvePractitonerResponse){
            String responseObject =((PractitionerOrchestratorActor.ResolvePractitonerResponse)msg).getResponseObject();
            finalizeRequest(responseObject);
        }
        else
        {
            unhandled(msg);
        }
    }
}
