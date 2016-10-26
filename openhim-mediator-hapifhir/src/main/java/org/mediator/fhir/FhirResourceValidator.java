package org.mediator.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.IGenericClient;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ValidationResult;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Identifier;
import org.hl7.fhir.dstu3.model.Practitioner;
import org.hl7.fhir.dstu3.model.Resource;
//import ca.uhn.fhir.model.api.R;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Created by server-hit on 10/22/16.
 */
public class FhirResourceValidator  {

    public void set_jsonResource(StringBuilder _jsonResource) {
        this._jsonResource = _jsonResource;
    }

    StringBuilder _jsonResource;

    public FhirContext getContext() {
        return _context;
    }

    FhirContext _context;
    IParser _parser;

    public List<Practitioner> getListOfPractitioners() {
        return listOfPractitioners;
    }

    List<Practitioner> listOfPractitioners=new ArrayList<Practitioner>();

    public List<Practitioner> getListOfInvalidePractitioners() {
        return listOfInvalidePractitioners;
    }

    public List<Practitioner> getListOfValidePractitioners() {
        return listOfValidePractitioners;
    }

    List<Practitioner> listOfValidePractitioners= new ArrayList<Practitioner>();

    public StringBuilder get_jsonResource() {
        return _jsonResource;
    }

    List<Practitioner> listOfInvalidePractitioners= new ArrayList<Practitioner>();
    public FhirResourceValidator(StringBuilder jsonResource)
    {
        this._jsonResource=jsonResource;
        _context=FhirContext.forDstu3();
        _parser=_context.newJsonParser();
    }
    public FhirResourceValidator()
    {
        this._jsonResource=new StringBuilder();
        _context=FhirContext.forDstu3();
        _parser=_context.newJsonParser();
    }
    public boolean processBundleJsonResource(String filePathError) throws Exception
    {
        boolean isProcessed=false;
        if(this._jsonResource.toString().length()<2)
        {
            throw new Exception("Invalide  Json resource");
        }
        Bundle oBundle=_parser.parseResource(Bundle.class,this._jsonResource.toString());
        if(oBundle!=null )
        {
            if(oBundle.getEntry().size()==0)
            {
                throw new Exception("No entries found in the Bundle");
            }
            else if (oBundle.getEntry().size()>0)
            {
                FhirResourceProcessor fhirProcessor=new FhirResourceProcessor(oBundle);
                fhirProcessor.processPractitionerBundle();
                listOfPractitioners=fhirProcessor.getListOfPractitioner();

                //Get the Valide Resource
                FhirValidator validator=this._context.newValidator();
                ResourcesValidation resValidation=new ResourcesValidation(validator,listOfPractitioners);
                resValidation.startValidation();
                listOfValidePractitioners=resValidation.getlistOfValidePractitioner();
                listOfInvalidePractitioners=resValidation.getlistOfInvalidePractitioner();
                if(listOfInvalidePractitioners.size()>0)
                {
                    //Create a new Bundle
                    Bundle invalidResourceBundle=new Bundle();
                    invalidResourceBundle.setType(Bundle.BundleType.COLLECTION);
                    for(Practitioner invalidPractitioner: listOfInvalidePractitioners)
                    {
                        Bundle.BundleEntryComponent oBundleEntry= new Bundle.BundleEntryComponent().setResource(invalidPractitioner);
                        invalidResourceBundle.addEntry(oBundleEntry);
                    }
                    char[] stringArray= _parser.encodeResourceToString(invalidResourceBundle).toCharArray();
                    StringBuilder strBuilder=new StringBuilder();
                    int compter=0;
                    for(char oCharacter:stringArray)
                    {
                        strBuilder.append(oCharacter);
                        compter++;
                    }
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss");
                    Date currentDate = new Date();
                    String fileName="bundle_invalide_practitioners-"+ dateFormat.format(currentDate);
                    filePathError+="/"+fileName;
                    ManageJsonFile.saveResourceInJSONFile(strBuilder,compter,filePathError);
                }
                isProcessed=true;
            }
        }
        else
        {
            throw new Exception("Failed to parse the json string to Bundle");
        }
        return isProcessed;
    }
    public boolean processBundleJsonResource(String filePathError,String sourceServerURI) throws Exception
    {
        boolean isProcessed=false;
        if(this._jsonResource.toString().length()<2)
        {
            throw new Exception("Invalide  Json resource");
        }
        Bundle oBundle=_parser.parseResource(Bundle.class,this._jsonResource.toString());
        if(oBundle!=null )
        {
            if(oBundle.getEntry().size()==0)
            {
                throw new Exception("No entries found in the Bundle");
            }
            else if (oBundle.getEntry().size()>0)
            {
                FhirResourceProcessor fhirProcessor=new FhirResourceProcessor(oBundle);
                fhirProcessor.processPractitionerBundle();
                listOfPractitioners=fhirProcessor.getListOfPractitioner();
                if(oBundle.getLink(Bundle.LINK_NEXT)!=null)
                {
                    IGenericClient oClient=this._context.newRestfulGenericClient(sourceServerURI);

                    //There is additional ressource to Extract
                    Bundle nextPageBundle=oClient.loadPage().next(oBundle).execute();
                    fhirProcessor=null;
                    fhirProcessor=new FhirResourceProcessor(nextPageBundle);
                    fhirProcessor.processPractitionerBundle();
                    listOfPractitioners.addAll(fhirProcessor.getListOfPractitioner());
                    while (nextPageBundle.getLink(Bundle.LINK_NEXT)!=null)
                    {
                        Bundle subNextBundle=oClient.loadPage().next(nextPageBundle).execute();
                        fhirProcessor=null;
                        fhirProcessor=new FhirResourceProcessor(subNextBundle);
                        fhirProcessor.processPractitionerBundle();
                        listOfPractitioners.addAll(fhirProcessor.getListOfPractitioner());
                        nextPageBundle=subNextBundle.copy();
                    }
                }
                //Get the Valide Resource
                FhirValidator validator=this._context.newValidator();
                ResourcesValidation resValidation=new ResourcesValidation(validator,listOfPractitioners);
                resValidation.startValidation();
                listOfValidePractitioners=resValidation.getlistOfValidePractitioner();
                listOfInvalidePractitioners=resValidation.getlistOfInvalidePractitioner();
                if(listOfInvalidePractitioners.size()>0)
                {
                    //Create a new Bundle
                    Bundle invalidResourceBundle=new Bundle();
                    invalidResourceBundle.setType(Bundle.BundleType.COLLECTION);
                    for(Practitioner invalidPractitioner: listOfInvalidePractitioners)
                    {
                        Bundle.BundleEntryComponent oBundleEntry= new Bundle.BundleEntryComponent().setResource(invalidPractitioner);
                        invalidResourceBundle.addEntry(oBundleEntry);
                    }
                    char[] stringArray= _parser.encodeResourceToString(invalidResourceBundle).toCharArray();
                    StringBuilder strBuilder=new StringBuilder();
                    int compter=0;
                    for(char oCharacter:stringArray)
                    {
                        strBuilder.append(oCharacter);
                        compter++;
                    }
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss");
                    Date currentDate = new Date();
                    String fileName="bundle_invalide_practitioners-"+ dateFormat.format(currentDate);
                    filePathError+="/"+fileName;
                    ManageJsonFile.saveResourceInJSONFile(strBuilder,compter,filePathError);
                }
                isProcessed=true;
            }
        }
        else
        {
            throw new Exception("Failed to parse the json string to Bundle");
        }
        return isProcessed;
    }
    public List<Practitioner> extractPractitionerFromBundleString(String bundleJsonString) throws Exception
    {
        List<Practitioner> extractedPractitioner=new ArrayList<>();
        if(bundleJsonString.length()<2)
        {
            throw new Exception("Invalide  Json resource");
        }
        Bundle oBundle=_parser.parseResource(Bundle.class,bundleJsonString.toString());
        if(oBundle!=null ) {
            if (oBundle.getEntry().size() == 0) {
                return extractedPractitioner;
            }
            else if (oBundle.getEntry().size()>0)
            {
                FhirResourceProcessor fhirProcessor=new FhirResourceProcessor(oBundle);
                fhirProcessor.processPractitionerBundle();
                extractedPractitioner=fhirProcessor.getListOfPractitioner();
            }

        }


        return extractedPractitioner;
    }

    public boolean bundleStringContainsPractitionerEntry(String bundleJsonString) throws Exception
    {
        boolean containsEntry=false;
        if(bundleJsonString.length()<2)
        {
            throw new Exception("Invalide  Json resource");
        }
        Bundle oBundle=_parser.parseResource(Bundle.class,bundleJsonString.toString());
        if(oBundle!=null ) {
            if (oBundle.getEntry().size() == 0) {
                return false;
            }
            else if (oBundle.getEntry().size()>0)
            {
                containsEntry=true;
            }

        }

        return containsEntry;
    }

}
class FhirResourceProcessor
{

    List<Resource> _listOfResources;
    Bundle _oBundle;
    public List<Resource> getListOfResources() {
        return _listOfResources;
    }
    public List<Practitioner> getListOfPractitioner() {
        return _listOfPractitioner;
    }

    public void setListOfPractitioner(List<Practitioner> _listOfPractitioner) {
        this._listOfPractitioner = _listOfPractitioner;
    }

    List<Practitioner> _listOfPractitioner;

    public Bundle get_oBundle() {
        return _oBundle;
    }

    public void set_oBundle(Bundle _oBundle) {
        this._oBundle = _oBundle;
    }


    public FhirResourceProcessor(Bundle oBundle)
    {
        this._oBundle=oBundle;
        this._listOfResources=new ArrayList<Resource>();
        //Extract all the resources in the Bundle
        for(Bundle.BundleEntryComponent entry: this._oBundle.getEntry())
        {
            this._listOfResources.add(entry.getResource());

        }
    }
    //Operation Type 1: insert,2:update
    public static String extractResponseStaticsFromBundleTransactionRespons(Bundle bundleResponse, int operationType) throws Exception
    {
        int succes=0;
        int failed=0;
        String res="";

        int total=bundleResponse.getEntry().size();
        for(Bundle.BundleEntryComponent entry: bundleResponse.getEntry())
        {
            if(operationType==1)
            {
                if(entry.getResponse().getStatus().trim().toUpperCase().contains("201 CREATED"))
                {
                    succes++;
                }
                else
                {
                    failed++;
                }
            }
            else
            {
                if(entry.getResponse().getStatus().trim().contains("200 OK"))
                {
                    succes++;
                }
                else
                {
                    failed++;
                }
            }

        }
        if(operationType==1)
        {
            res ="{id:insert operation,";
        }
        else if (operationType==2)
        {
            res ="{id:update operation,";
        }
        res+="total:"+total+"," +
                "succes:"+succes+"," +
                "failed:"+failed+"}";
        return res;

    }

    public void processPractitionerBundle()
    {
        //Extract All the Practitioner Resources
        this._listOfPractitioner=new ArrayList<Practitioner>();
        for (Resource oResource:this._listOfResources){
            if(oResource instanceof Practitioner)
            {
                this._listOfPractitioner.add((Practitioner)oResource);
            }
            else
            {
                continue;
            }
        }

    }
    public static String createPractitioner(FhirContext oContext,List<Practitioner> listOfPractitioners,String serverUrl) throws Exception
    {
        Bundle respOutcome=null;
        String stringTransactionResult="{TransactionResultStatus:no}";
        try
        {

            Bundle resourceBundle=new Bundle();
            resourceBundle.setType(Bundle.BundleType.TRANSACTION);
            int compter=0;
            for(Practitioner oPractitioner: listOfPractitioners)
            {
                Bundle.BundleEntryComponent oBundleEntry= new Bundle.BundleEntryComponent().setResource(oPractitioner);
                oBundleEntry.setFullUrl(oPractitioner.getId());
                oBundleEntry.getRequest().setUrl("Practitioner").setMethod(Bundle.HTTPVerb.POST);
                resourceBundle.addEntry(oBundleEntry);
                compter++;
                //if(compter==5) break;
            }
            String filePath1="/home/server-hit/Desktop/Bundle.json";
            //ManageJsonFile.saveResourceInJSONFile(resourceBundle,oContext,filePath1);
            IGenericClient client=oContext.newRestfulGenericClient(serverUrl);

            respOutcome=client.transaction().withBundle(resourceBundle).execute();
            System.out.print(oContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(respOutcome));
            if(respOutcome!=null)
            {
                stringTransactionResult=FhirResourceProcessor.extractResponseStaticsFromBundleTransactionRespons(respOutcome,1);
            }

        }
        catch (Exception exc)
        {
            throw new Exception(exc.getMessage());
        }
        return  stringTransactionResult;
    }
    public static String updatePractitionerInTransaction(FhirContext oContext,List<Practitioner> listOfPractitioners,String serverUrl) throws Exception
    {
        Bundle respOutcome=null;
        String stringTransactionResult="{TransactionResultStatus:no}";
        try
        {

            Bundle resourceBundle=new Bundle();
            resourceBundle.setType(Bundle.BundleType.TRANSACTION);
            int compter=0;
            for(Practitioner oPractitioner: listOfPractitioners)
            {
                Bundle.BundleEntryComponent oBundleEntry= new Bundle.BundleEntryComponent().setResource(oPractitioner);
                oBundleEntry.setFullUrl(oPractitioner.getId());
                List <Identifier> listOfIdentifier=oPractitioner.getIdentifier();
                String searchPattern="Practitioner?";
                for(Identifier oIdentifier :listOfIdentifier) {
                    String idSystem = oIdentifier.getSystem();
                    String idValue = oIdentifier.getValue();
                    if(idSystem==null)
                    {
                        searchPattern+="identifier="+idValue;
                    }
                    else
                    {
                        searchPattern+="identifier="+idSystem+"|"+idValue;
                    }
                    oBundleEntry.getRequest().setUrl(searchPattern).setMethod(Bundle.HTTPVerb.PUT);
                    //
                    //break;
                }
                //String searchPattern=FhirMediatorUtilities.buildPractitionerSearchRequestByIdentifier()
                //oBundleEntry.getRequest().setUrl(searchPattern).setMethod(Bundle.HTTPVerb.PUT);
                resourceBundle.addEntry(oBundleEntry);
                compter++;
            }
            //ManageJsonFile.saveResourceInJSONFile(resourceBundle,oContext,filePath1);
            IGenericClient client=oContext.newRestfulGenericClient(serverUrl);
            System.out.print(oContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(resourceBundle));
            respOutcome=client.transaction().withBundle(resourceBundle).execute();
            //client.transaction().withBundle(resourceBundle).ex
            //client.transaction().withBundle(resourceBundle).ex
            //client.transaction().withResources(listOfPractitioners).execute();
            System.out.print(oContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(respOutcome));
            if(respOutcome!=null)
            {
                stringTransactionResult=FhirResourceProcessor.extractResponseStaticsFromBundleTransactionRespons(respOutcome,2);
            }

            //respOutcome.gere


        }
        catch (Exception exc)
        {
            throw new Exception(exc.getMessage());
        }
        return stringTransactionResult;
    }
    public static String updatePractitionerInTransaction(FhirContext oContext, Map<String,Practitioner> mapCouplePractitionerAndIdToUpdate, String serverUrl) throws Exception
    {
        Bundle respOutcome=null;
        String stringTransactionResult="{TransactionResultStatus:no}";
        try
        {

            Bundle resourceBundle=new Bundle();
            resourceBundle.setType(Bundle.BundleType.TRANSACTION);
            int compter=0;

            for (Practitioner oPractitioner:mapCouplePractitionerAndIdToUpdate.values())
            {
                String searchPattern="Practitioner?";
                String key=null;
                //getKey
                for(String oKey :mapCouplePractitionerAndIdToUpdate.keySet())
                {
                    if(mapCouplePractitionerAndIdToUpdate.get(oKey).equals(oPractitioner))
                    {
                        key=oKey;
                        break;
                    }
                }
                if(key!=null)
                {
                    searchPattern+="identifier="+key;
                    Bundle.BundleEntryComponent oBundleEntry= new Bundle.BundleEntryComponent().setResource(oPractitioner);
                    oBundleEntry.setFullUrl(oPractitioner.getId());
                    oBundleEntry.getRequest().setUrl(searchPattern).setMethod(Bundle.HTTPVerb.PUT);
                    resourceBundle.addEntry(oBundleEntry);
                }

            }
            //ManageJsonFile.saveResourceInJSONFile(resourceBundle,oContext,filePath1);
            IGenericClient client=oContext.newRestfulGenericClient(serverUrl);
            System.out.print(oContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(resourceBundle));
            respOutcome=client.transaction().withBundle(resourceBundle).execute();
            //client.transaction().withBundle(resourceBundle).ex
            //client.transaction().withBundle(resourceBundle).ex
            //client.transaction().withResources(listOfPractitioners).execute();
            System.out.print(oContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(respOutcome));
            if(respOutcome!=null)
            {
                stringTransactionResult=FhirResourceProcessor.extractResponseStaticsFromBundleTransactionRespons(respOutcome,2);
            }

            //respOutcome.gere


        }
        catch (Exception exc)
        {
            throw new Exception(exc.getMessage());
        }
        return stringTransactionResult;
    }
    public static Bundle updatePractitionerWithIdentifierParameter(FhirContext oContext,List<Practitioner> listOfPractitioners,String serverUrl)
    {
        Bundle respOutcome=null;
        try
        {

            Bundle resourceBundle=new Bundle();
            int compter=0;
            IGenericClient client=oContext.newRestfulGenericClient(serverUrl);
            for(Practitioner oPractitioner: listOfPractitioners)
            {

                List <Identifier> listOfIdentifier=oPractitioner.getIdentifier();
                String searchPattern="Practitioner?";
                for(Identifier oIdentifier :listOfIdentifier) {
                    String idSystem = oIdentifier.getSystem();
                    String idValue = oIdentifier.getValue();
                    MethodOutcome outcome=null;
                    outcome=client.update().resource(oPractitioner).
                            conditional().where(oPractitioner.IDENTIFIER.
                            exactly().
                            identifier(idValue)).execute();
                    /*
                    if(idSystem!=null)
                    {
                        outcome=client.update().resource(oPractitioner).
                                conditional().where(oPractitioner.IDENTIFIER.
                                exactly().
                                systemAndIdentifier(idSystem,idValue)).execute();
                    }
                    else
                    {
                        outcome=client.update().resource(oPractitioner).
                                conditional().where(oPractitioner.IDENTIFIER.
                                exactly().
                                identifier(idSystem)).execute();
                    }*/
                    searchPattern=FhirMediatorUtilities.encodeUrlToHttpFormat(searchPattern);
                    /*
                    MethodOutcome outcome=client.update().resource(oPractitioner).
                            conditionalByUrl(searchPattern).
                            execute();
                     */

                    System.out.println(outcome.getId());

                    break;
                }
            }

            System.out.print(oContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(respOutcome));


        }
        catch (Exception exc)
        {
            System.out.print(exc.getStackTrace());
        }
        return respOutcome;
    }
    //public static String UpdatePractitioner
    private  static void identifyPractitionerToUpdate(Practitioner oPractitioner,IGenericClient oClient)
    {
        try
        {
            //Check if practitioner has an identifier and start search with it
            TokenClientParam test;
            if(oPractitioner.getIdentifier().size()>0)
            {
                //Identifier test;
                for (Identifier oIdentifier:oPractitioner.getIdentifier()) {

                }

            }

        }
        catch (Exception exc)
        {

        }
    }

}
class ResourcesValidation
{
    FhirValidator _validator;
    List<Practitioner> _listOfPractitionersToValidate;

    public List<Practitioner> getlistOfValidePractitioner() {
        return _listOfValidePractitioner;
    }

    List<Practitioner> _listOfValidePractitioner;

    public List<Practitioner> getlistOfInvalidePractitioner() {
        return _listOfInvalidePractitioner;
    }

    List<Practitioner> _listOfInvalidePractitioner;

    public ResourcesValidation(FhirValidator validator,List<Practitioner> listOfPractitionersToValidate)
    {
        this._validator=validator;
        this._listOfPractitionersToValidate=listOfPractitionersToValidate;
        this._listOfValidePractitioner=new ArrayList<Practitioner>();
        this._listOfInvalidePractitioner=new ArrayList<Practitioner>();
    }
    public void startValidation()
    {
        for(Practitioner oPractionerToValidate: this._listOfPractitionersToValidate)
        {
            ValidationResult res=null;
            res=this._validator.validateWithResult(oPractionerToValidate);
            if(res.isSuccessful())
            {
                this._listOfValidePractitioner.add(oPractionerToValidate);
            }
            else
            {
                this._listOfInvalidePractitioner.add(oPractionerToValidate);
            }

        }
    }

}
class ManageJsonFile
{
    String _filePath;

    public ManageJsonFile(String filePath)
    {
        this._filePath=filePath;
    }
    public String returnJsonFileContent()
    {
        try
        {
            String absolutePath=this._filePath;
            File fHandler=new File(absolutePath);
            if(fHandler.exists())
            {
                FileInputStream f =new FileInputStream(absolutePath);
                StringBuilder sb = new StringBuilder();
                BufferedReader br=new BufferedReader(new FileReader(absolutePath));
                String line=br.readLine();
                while (line != null) {
                    sb.append(line);
                    line=br.readLine();
                }
                return sb.toString();
            }
            else
            {
                return null;
            }
        }
        catch (IOException exc)
        {
            System.out.print(exc.getStackTrace());
        }
        return  null;
    }
    public static void saveResourceInJSONFile(StringBuilder strBuilder,String filePath)
    {
        try
        {
            FileWriter file=new FileWriter(filePath);
            System.out.print(strBuilder.toString());
            file.write(strBuilder.toString().toCharArray());
        }
        catch (IOException exc){
            System.out.print(exc.getStackTrace());

        }
        catch (Exception exc)
        {
            System.out.print(exc.getStackTrace());
        }
    }
    public static void saveResourceInJSONFile(StringBuilder strBuilder,int strBuiderSize,String filePath)
    {
        try
        {
            BufferedWriter writer=new BufferedWriter(new FileWriter(filePath));
            //System.out.print(strBuilder.toString());
            char[] charTab=new char[strBuiderSize];
            for(int i=0;i<strBuiderSize;i++)
            {
                charTab[i]=strBuilder.charAt(i);
                //System.out.print(strBuilder.charAt(i));
                writer.write(""+strBuilder.charAt(i));
            }
            writer.close();
        }
        catch (IOException exc){
            System.out.print(exc.getStackTrace());

        }
        catch (Exception exc)
        {
            System.out.print(exc.getStackTrace());
        }
    }
    public static void saveResourceInJSONFile(Bundle oBuncle,FhirContext oContext,String filePath)
    {
        try
        {
            IParser oParser = oContext.newJsonParser();
            char[] stringArray= oParser.encodeResourceToString(oBuncle).toCharArray();
            int strBuiderSize=stringArray.length;
            StringBuilder strBuilder=new StringBuilder();
            for(char oCharacter:stringArray)
            {
                strBuilder.append(oCharacter);
            }
            BufferedWriter writer=new BufferedWriter(new FileWriter(filePath));
            //System.out.print(strBuilder.toString());
            char[] charTab=new char[strBuiderSize];
            for(int i=0;i<strBuiderSize;i++)
            {
                charTab[i]=strBuilder.charAt(i);
                //System.out.print(strBuilder.charAt(i));
                writer.write(""+strBuilder.charAt(i));
            }
            writer.close();
        }
        catch (IOException exc){
            System.out.print(exc.getStackTrace());

        }
        catch (Exception exc)
        {
            System.out.print(exc.getStackTrace());
        }
    }
}
