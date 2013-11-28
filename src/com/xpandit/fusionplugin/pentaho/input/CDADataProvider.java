package com.xpandit.fusionplugin.pentaho.input;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.pentaho.commons.connection.IPentahoResultSet;
import org.pentaho.platform.api.repository2.unified.IUnifiedRepository;
import org.pentaho.platform.api.repository2.unified.RepositoryFile;
import org.pentaho.platform.api.repository2.unified.UnifiedRepositoryException;
import org.pentaho.platform.engine.core.system.PentahoSystem;

import pt.webdetails.cda.CdaQueryComponent;

import com.xpandit.fusionplugin.PropertiesManager;
import com.xpandit.fusionplugin.exception.InvalidDataResultSetException;
import com.xpandit.fusionplugin.exception.InvalidParameterException;

// TODO call cda as bean on the platform
// TODO review exception handling to getter clearer errors. Catch after throw...

/**
 * Class that gathers data based on CDA files.
 * 
 * @author <a href="mailto:rplp@xpand-it.com">rplp</a>
 * @version $Revision: 666 $
 * 
 */
public class CDADataProvider extends DataProvider {

    private static final String CDAPATH = "cdaPath";
    private static final String CDAPARAMETERS = "cdaParameters";
    private static final String TARGETVALUECDAID = "targetValueCdaId";
    private static final String RANGEVALUECDAID = "rangeValueCdaId";
    private static final String CDAID = "cdaDataAccessId";
    private static final String CDAOUTPUTID = "outputIndexId";
    private static final String DATASTAMP = "dataStamp";

    private PropertiesManager pm = null;
    private CdaQueryComponent cdaQueryComponent;

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.xpandit.fusionplugin.pentaho.input.DataProvider#getResultSets(com.xpandit.fusionplugin.PropertiesManager)
     */

    @Override
    public Map<String, ArrayList<IPentahoResultSet>> getResultSet(PropertiesManager pm)
            throws InvalidDataResultSetException, InvalidParameterException {
        this.pm = pm;

        IUnifiedRepository repository = PentahoSystem.get(IUnifiedRepository.class);
        RepositoryFile file = getCDAFile(repository);

        boolean outputIndexIdDefined = false;
        Map<String, ArrayList<IPentahoResultSet>> resultSets = new TreeMap<String, ArrayList<IPentahoResultSet>>();

        cdaQueryComponent = new CdaQueryComponent();
        cdaQueryComponent.setFile(file.getPath());

        Map<String, Object> cdaInputs = cdaParameters();

        IPentahoResultSet resultset = null;

        if (pm.getParams().get(CDAID) == null) {
            throw new InvalidParameterException(InvalidParameterException.ERROR_006 + CDAID);
        }

        if (pm.getParams().get(CDAOUTPUTID) != null) {
            outputIndexIdDefined = true;
        }

        // get dataAccessIDs using properties manager
        String[] queryIDs = ((String) pm.getParams().get(CDAID)).split(";");
        String[] outputIndexIds = null;

        if (outputIndexIdDefined) {
            // get outputIndexIds from request
            outputIndexIds = ((String) pm.getParams().get(CDAOUTPUTID)).split(";");
            // if there is an indexDefined than we must make sure they have the same size
            if (outputIndexIds.length != queryIDs.length) {
                throw new InvalidParameterException(InvalidParameterException.ERROR_007 + "\n Number of accessIds -> "
                        + outputIndexIds.length + "\n Number of outputIndexIds -> " + outputIndexIds.length);
            }
        }

        ArrayList<IPentahoResultSet> aux = new ArrayList<IPentahoResultSet>();
        int iteration = 0;
        for (String queryID : queryIDs) {

            // set data access id
            cdaInputs.put("dataAccessId", queryID);
            if (outputIndexIdDefined) {
                cdaInputs.put("outputIndexId", outputIndexIds[iteration]);
            }
            cdaQueryComponent.setInputs(cdaInputs);

            // execute CDA
            Boolean result = null;
            try {
                result = cdaQueryComponent.execute();
            } catch (Exception e) {
                throw new InvalidDataResultSetException("Cannot execute CDA query", "Query ID:" + queryID);
            }

            if (result) {
                resultset = cdaQueryComponent.getResultSet();
                aux.add(resultset);

                if (resultset == null) {
                    throw new InvalidDataResultSetException(InvalidDataResultSetException.ERROR_003,
                            "resultset==null Query ID:" + queryID);
                }
            }
            
            ++iteration;
        }
        resultSets.put("results", aux);

        // get the targetValue result set if targetValueCdaId property exists
        try {
            if (pm.getParams().containsKey(TARGETVALUECDAID))
                resultSets.put("targetValue", getTargetValueCDA(cdaInputs, resultset));
        } catch (Exception e) {
            getLogger().error(
                    "Error retrieving data: cdaQueryComponent failed to return data. Query ID" + TARGETVALUECDAID, e);
        }

        // get the targetValue result set if rangeValueCdaId property exists
        if (pm.getParams().containsKey(RANGEVALUECDAID))
            resultSets.put("rangeValues", getRangeValuesCDA(cdaInputs, resultset));

        return resultSets;
    }

    /**
     * Gets CDA file using the cdaPath parameter
     * 
     * @param repository
     * @return
     * @throws InvalidParameterException
     */
    private RepositoryFile getCDAFile(final IUnifiedRepository repository) throws InvalidParameterException {
        String cdaPath = (String) pm.getParams().get(CDAPATH);
        RepositoryFile file = null;

        if (cdaPath == null) {
            throw new InvalidParameterException(InvalidParameterException.ERROR_006 + CDAPATH
                    + " parameter not supplied.");
        }

        try {
            file = repository.getFile(cdaPath);
        } catch (UnifiedRepositoryException e) {
            throw new InvalidParameterException(InvalidParameterException.ERROR_005 + "No solution file found: "
                    + cdaPath);
        }

        if(file == null){
            throw new InvalidParameterException(InvalidParameterException.ERROR_005 + "No solution file found: "
                    + cdaPath);
        }
        
        return file;
    }

    /**
     * 
     * Invoke the CDA to get the Target Value of a chart
     * 
     * @param cdaInputs
     * @param resultset
     * @return
     * @throws Exception
     */
    // TODO requires refactoring -> CDA code is being called too many times.
    private ArrayList<IPentahoResultSet> getTargetValueCDA(Map<String, Object> cdaInputs, IPentahoResultSet resultset)
            throws Exception {
        ArrayList<IPentahoResultSet> aux = new ArrayList<IPentahoResultSet>();
        // invoke to get target value
        String queryID = (String) pm.getParams().get(TARGETVALUECDAID);
        // set data access id
        cdaInputs.put("dataAccessId", queryID);
        cdaQueryComponent.setInputs(cdaInputs);

        // execute query
        if (cdaQueryComponent.execute()) {
            resultset = cdaQueryComponent.getResultSet();
            aux.add(resultset);
        }
        return aux;
    }

    /**
     * 
     * Invoke the CDA to get the list of range colors and the base value to calculate the range values
     * 
     * @param cdaInputs
     * @param resultset
     * @return
     */
    // TODO requires refactoring -> CDA code is being called to many times.
    private ArrayList<IPentahoResultSet> getRangeValuesCDA(Map<String, Object> cdaInputs, IPentahoResultSet resultset)
            throws InvalidDataResultSetException {
        ArrayList<IPentahoResultSet> aux = new ArrayList<IPentahoResultSet>();
        String queryID = (String) pm.getParams().get(RANGEVALUECDAID);
        // invoke to get ranges values

        String[] queryIDArray = queryID.split(";");
        for (int i = 0; i < queryIDArray.length; i++) {
            try {
                // set data access id
                cdaInputs.put("dataAccessId", queryIDArray[i]);
                cdaQueryComponent.setInputs(cdaInputs);

                // execute query
                if (cdaQueryComponent.execute()) {
                    resultset = cdaQueryComponent.getResultSet();
                    aux.add(resultset);
                }
            } catch (Exception e) {
               throw new InvalidDataResultSetException(InvalidDataResultSetException.ERROR_004,"Querie ID: " + RANGEVALUECDAID);
            }
        }

        return aux;
    }

    /**
     * Get all parameter Values and return a map that can be used as CDA parameters
     * 
     * @return return parameters as requested by CDA
     */
    private HashMap<String, Object> cdaParameters() {
        HashMap<String, Object> cdaParameters = new HashMap<String, Object>();
        TreeMap<String, Object> params = pm.getParams();
        String parameterKeys = (String) params.get(CDAPARAMETERS);
        if (parameterKeys == null) {
            getLogger().debug("No parameters will be passed: " + CDAPARAMETERS + " don't exist");
            return cdaParameters;
        }

        // forward the dataStamp to CDA if it exists in the parameters
        if (params.get(DATASTAMP) != null) {
            parameterKeys = parameterKeys + ";" + DATASTAMP;
        }

        StringBuffer cdaParameterString = new StringBuffer();

        String[] parametersKeysArray = parameterKeys.split(";");
        for (int i = 0; i < parametersKeysArray.length; i++) {
            Object value = params.get(parametersKeysArray[i]);
            if (value == null)
                new InvalidParameterException(InvalidParameterException.ERROR_003 + " with key:"
                        + parametersKeysArray[i]);
            else {
                // if is string just set the string
                if (value instanceof String)
                    cdaParameterString.append(parametersKeysArray[i]).append("=").append(value).append(";");
                else { // if it's a list set all the elements
                    String[] listValue = (String[]) value;
                    for (String valueElement : listValue) {
                        cdaParameterString.append(parametersKeysArray[i]).append("=").append(valueElement).append(";");
                    }
                }
            }
        }
        cdaParameters.put("cdaParameterString", cdaParameterString.toString());

        return cdaParameters;
    }

    public Log getLogger() {
        return LogFactory.getLog(CDADataProvider.class);
    }

    @Override
    public Map<String, ArrayList<IPentahoResultSet>> getResultSetsRange(PropertiesManager pm)
            throws InvalidDataResultSetException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<String, ArrayList<IPentahoResultSet>> getResultSetsTarget(PropertiesManager pm)
            throws InvalidDataResultSetException {
        return null;
    }

}
