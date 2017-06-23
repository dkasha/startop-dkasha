package com.ericsson.ntf.ext.webservices;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.bss.cil.cdal.ntf.Interface.ReadFrom;
import com.ericsson.bss.cil.cdal.ntf.service.domain.NotificationDomain.Notification;
import com.ericsson.bss.cil.cdal.ntf.service.domain.TemplateExtNewDomain.TemplateBase;
import com.ericsson.bss.cil.cdal.ntf.service.domain.TemplateExtNewDomain.Template_v2;
import com.ericsson.bss.cil.cdal.ntf.service.domain.TemplateIdDomain.Template;
import com.ericsson.cel.impl.schema.SchemaConfigurationService;
import com.ericsson.ntf.common.util.NtfUtils;
import com.ericsson.ntf.configAgent.DataAccessEnablerInterface;
import com.ericsson.ntf.schema.NotifSchemaLookup;

/**
 * This class is the REST interface to be used by the external PAC's . It supports only the READ
 * operation to get the list of templates for a particular PAC, a subset of PAC's or all PAC's
 * 
 * @author eashapr
 * 
 */

@Path("/ntf/notificationTemplateEnquiry/v1/notificationTemplate")
public class NtfTemplateRestExtNew {

    private static final String SCHEMA_NAMESPACE = "com.ericsson.cel.cer.ntf";

    private static final Logger LOG = LoggerFactory.getLogger(NtfTemplateRestExtNew.class);

    @Context
    UriInfo uriInfo;
    @Context
    HttpServletRequest request;

    @Context
    HttpServletResponse response;

    private DataAccessEnablerInterface daeIntf;

    SchemaConfigurationService schemaService;

    // Version 1.0 will return template id+keywords
    protected static final String VERSION_1 = "v1";
    // Version 2.0 will return template id + category
    protected static final String VERSION_2 = "v2";
    // Version 3 will return template id+ category+keywords
    protected static final String VERSION_3 = "v3";

    // Header in the Request
    protected static final String REQUEST_HEADER_ACCEPT = "Accept";

    // Header in the Response
    protected static final String RESPONSE_HEADER_ContentType = "Content-Type";
    protected static final String RESPONSE_HEADER_AccessContorlAllowOrigin = "Access-Control-Allow-Origin";
    protected static final String RESPONSE_HEADER_CORS = "*";

    protected static final String profileErr = "profile=http://ericsson.com/bss.ntf.errorSchema.1.json#";
    protected static final String profileV1 = "profile=http://ericsson.com/bss.ntf.notificationTemplateEnquiry.1.json#";
    protected static final String profileV2 = "profile=http://ericsson.com/bss.ntf.notificationTemplateEnquiry.2.json#";
    protected static final String profileV3 = "profile=http://ericsson.com/bss.ntf.notificationTemplateEnquiry.3.json#";
    protected static final String HEADER_PREFIX = "application/json; charset=utf-8; ";

    // Value in Response header for an error scenario
    protected static final String RESPONSE_HEADER_ERROR = HEADER_PREFIX + profileErr;
    // Value in Response header for a success scenario
    protected static final String RESPONSE_HEADER_SUCCESS_v1 = HEADER_PREFIX + profileV1;
    protected static final String RESPONSE_HEADER_SUCCESS_v2 = HEADER_PREFIX + profileV2;
    protected static final String RESPONSE_HEADER_SUCCESS_v3 = HEADER_PREFIX + profileV3;

    // Error Code Label
    protected static final String RESPONSE_HEADER_ERROR_CODE = "Ntf-Error-Code";
    protected static final String RESPONSE_HEADER_ERROR_MESSAGE = "Ntf-Error-Message";

    // Error Codes
    protected static final String NOT_FOUND_CODE = "ntf.notificationTemplateEnquiry.templateNotFound";
    protected static final String INVALID_TEMPLATE_VERSION = "ntf.notificationTemplateEnquiry.invalidTemplateVersion";
    protected static final String INVALID_CATEGORY = "ntf.notificationTemplateEnquiry.invalidCategory";
    protected static final String EXCEPTION_CODE = "ntf.notificationTemplateEnquiry.Exception";

    public NtfTemplateRestExtNew(DataAccessEnablerInterface dae) {
	daeIntf = dae;
    }

    /**
     * Method to get list of templates for a given or all ApplicationID (PAC).
     * 
     * @param acceptHeader
     * @return List of template Object.
     * @throws IOException
     */
    @GET
    @Produces("application/json")
    public Response getData(@HeaderParam("Accept") String acceptHeader) throws IOException {
	LOG.info("getData invoked");
	LOG.debug("acceptHeader = '{}'", acceptHeader);

	String version = getSchemaVersion(acceptHeader);
	String appId = request.getParameter("applicationId");
	String category = request.getParameter("category");

	LOG.debug("appId='{}', category='{}', version='{}'", appId, category, version);

	if (appId == null || appId.length() == 0) {
	    appId = "*";
	}
	if (category != null && category.length() == 0) {
	    return setErrorResponse(INVALID_CATEGORY, "category parameter set is empty");
	}

	List<TemplateBase> ret;
	try {
	    if (version == null || version.equals(profileV1)) {
		ret = getTemplateData(VERSION_2, appId, category);
	    } else {
		return setErrorResponse(INVALID_TEMPLATE_VERSION, "profile set in Accept Header Param is " + version);
	    }

	    return Response.ok().header(RESPONSE_HEADER_AccessContorlAllowOrigin, RESPONSE_HEADER_CORS)
		    .header(RESPONSE_HEADER_ContentType, acceptHeader).entity(ret).build();
	} catch (NtfRestException ntfEx) {
	    LOG.error("Exception handled: {}", ntfEx.getMessage());
	    LOG.trace("Exception stacktrace", ntfEx);
	    return setErrorResponse(ntfEx.getErrorCode(), ntfEx.getMessage());
	}
    }

    @OPTIONS
    public Response getOptions() {
	return Response.ok().header("Access-Control-Allow-Origin", "*")
		.header("Access-Control-Allow-Methods", "GET, OPTIONS")
		.header("Access-Control-Allow-Headers", "Content-Type, Accept, X-Requested-With").build();
    }

    private static String getSchemaVersion(String acceptString) {
	if (LOG.isDebugEnabled()) {
	    LOG.debug("acceptString = {}", acceptString);
	}
	int bIndex = acceptString.indexOf("profile=");
	int eIndex = acceptString.indexOf("json#", bIndex);
	if (bIndex == -1 || eIndex == -1) {
	    return null;
	}
	return acceptString.substring(bIndex, eIndex + 5);
    }

    /**
     * Method to return the list of template data
     * 
     * @param versionParam
     *            - V2 version of the template details to be returned in the response
     * @param appId
     *            - TPG id or comma separated list of TPG's
     * @param categoryParam
     *            - category to filter on
     * @return list of template data for the given TPG(s) and category
     * @throws IOException
     */
    private List<TemplateBase> getTemplateData(String versionParam, String appIds, String categoryParam)
	    throws NtfRestException {

	LOG.info("getTemplateData invoked");
	// Split the id - since it may be comma separated
	String[] appIdArr = appIds.split(",");
	LOG.debug("appIds size = {}", appIdArr.length);

	List<TemplateBase> retRows = null;

	try {
	    if (appIdArr.length > 0) {
		ArrayList<String> appIdList = new ArrayList<>();
		appIdList.addAll(Arrays.asList(appIdArr));
		// force reload of template cache
		List<Template> tempList = daeIntf.getAllTemplate(ReadFrom.SERVER);

		if (appIdList.contains("*")) {
		    LOG.debug("Getting all Template");
		} else {
		    tempList = new ArrayList<>();
		    for (String appId : appIdArr) {
			List<Template> templateForApp = daeIntf.getTemplateForApp(appId);
			if (templateForApp != null && templateForApp.size() > 0) {
			    tempList.addAll(templateForApp);
			}
		    }
		}
		if (tempList != null && tempList.size() > 0) {
		    retRows = getRowsFromTemplate(tempList, categoryParam, versionParam);
		}
		LOG.info("Number of template records = {} for category = {}, version = {}, appId = {}",
			(retRows == null ? 0 : retRows.size()), categoryParam, versionParam, appIds);
	    }

	} catch (Exception e) {
	    LOG.error("Exception in getting the Template list for applicationId {}", appIds, e);
	    throw new NtfRestException(EXCEPTION_CODE,
		    "Exception in getting the Template list for applicationId :" + appIds + " - " + e.getMessage());
	}
	if (retRows == null || retRows.size() == 0) {
	    String detailMsg;
	    if (categoryParam == null || categoryParam.length() == 0) {
		detailMsg = "applicationId:" + appIds + " not found.";
	    } else {
		detailMsg = "applicationId:" + appIds + " and category: " + categoryParam + " not found.";
	    }
	    throw new NtfRestException(NOT_FOUND_CODE, detailMsg);
	}
	return retRows;
    }

    private ArrayList<TemplateBase> getRowsFromTemplate(List<Template> templateList, String category, String version)
	    throws Exception {

	LOG.info("getRowsFromTemplate invoked with category = {}, verison = {} for template size = {}", category,
		version, templateList.size());

	ArrayList<TemplateBase> retList = new ArrayList<>();

	List<Notification> allNotification = daeIntf.getAllNotification(ReadFrom.SERVER);
	if (allNotification == null) {
	    // no active notification so no template/notification combinations
	    // to return
	    return retList;
	}
	LOG.info("Found number of Notifications = {}", allNotification.size());
	for (Template template : templateList) {
	    for (Notification notification : allNotification) {
		if (matches(template, notification, category)) {
		    TemplateBase row = createEntries(version, template, notification);
		    if (row != null) {
			if (!retList.contains(row)) {
			    retList.add(row);
			} else {
			    appendSchemaVersionList(retList, row);
			}
		    }
		}
	    }
	}
	return retList;

    }

    /**
     * Appending schema version list of the row with template present in retList
     * 
     * @param retList
     * @param row
     */
    private void appendSchemaVersionList(ArrayList<TemplateBase> retList, TemplateBase row) {
	if (row instanceof Template_v2) {
	    List<String> rowSchemaVersions = ((Template_v2) row).getSchemaVersions();
	    for (TemplateBase template : retList) {
		// If row matches with template present in list
		if (template.equals(row)) {
		    Template_v2 template_v2 = (Template_v2) template;
		    List<String> schemaVersions = template_v2.getSchemaVersions();
		    // Merging schemaVersions & rowSchemaVersionsin descending order
		    schemaVersions.removeAll(rowSchemaVersions);
		    schemaVersions.addAll(rowSchemaVersions);
		    Collections.sort(schemaVersions, new Comparator<String>() {
			@Override
			public int compare(String left, String right) {
			    String leftMajorV = left.split("\\.")[0];
			    String rightMajorV = right.split("\\.")[0];
			    return rightMajorV.compareTo(leftMajorV);
			}
		    });

		}
	    }
	}
    }

    private TemplateBase createEntries(String version, Template template, Notification notification) {
	switch (version) {
	case VERSION_2:
	    String appIdStr = NtfUtils.safeToString(template.getAppId());
	    String categoryStr = NtfUtils.safeToString(notification.getCategory());

	    Template_v2 row = new Template_v2();
	    row.setId(NtfUtils.safeToString(template.getTemplateId()));
	    row.setApplicationId(appIdStr);
	    row.setName(NtfUtils.safeToString(template.getTemplateName()));
	    row.setCategory(categoryStr);
	    row.setDescription(NtfUtils.safeToString(template.getDescription()));

	    setTemplateSchemaVersions(appIdStr, categoryStr, NtfUtils.safeToString(notification.getSchemaVersion()),
		    row);
	    if(row.getSchemaVersions() == null || row.getSchemaVersions().size() == 0){
		return null;
	    }
	    return row;
	default:
	    LOG.error("requested unsupported version {}", version);
	}
	return null;
    }

    private static boolean matches(Template template, Notification notification, String category) {
	LOG.debug("matching template {} to notification {} and  category {}", template, notification, category);
	return (Objects.equals(template.getTemplateId(), notification.getTemplateId())
		&& Objects.equals(template.getAppId(), notification.getAppId())
		&& (category == null || Objects.equals(category, NtfUtils.safeToString(notification.getCategory()))));
    }

    /**
     * Setting active schema version and major schema version list to Template from Zookeeper schema
     * service<br>
     * TODO determine if required
     * 
     * /**
     * 
     * @param appId
     * @param category
     * @param defaultSchemaVersion
     * @param c
     *            template_v2
     * @return
     */
    protected void setTemplateSchemaVersions(String appId, String category, String defaultSchemaVersion,
	    Template_v2 row) {
	List<String> versionList = new ArrayList<>();
	if (defaultSchemaVersion == null || appId == null || category == null) {
	    return;
	}
	if (!NtfUtils.isDefaultVersion(defaultSchemaVersion)) {
	    versionList.add(defaultSchemaVersion);
	}
	// If default version is type 'a.X.X' or 'a.b.X' or 'default'
	String schemaName = appId + "_" + category;
	if (schemaService == null) {
	    schemaService = NotifSchemaLookup.getSchemaService();
	}
	if (schemaService != null) {
	    try {
		String activeVersionForSchema = schemaService.getActiveVersionForSchema(SCHEMA_NAMESPACE, schemaName);
		row.setSchemaVersion(activeVersionForSchema);

		// if defaultSchemaVersion is not available in versionList, then get from shcema
		// service
		if (versionList.isEmpty()) {
		    List<String> list = schemaService.getVersionsForSchema(SCHEMA_NAMESPACE, schemaName, true, true);
		    if (list != null && list.size() > 0) {
			versionList.addAll(list);
		    }
		    versionList = NtfUtils.filterAllowedVersions(versionList, defaultSchemaVersion);
		    versionList = filterMajorVersions(versionList, defaultSchemaVersion);
		}
	    } catch (Exception e) {
	    }

	} else {
	    LOG.error("Schema Service is Not Available.");
	}

	row.setSchemaVersions(versionList);
    }

    /**
     * Sets the response object for an error scenario
     */
    private static Response setErrorResponse(String errCode, String message) {

	ResponseBuilder r = Response.noContent().header(RESPONSE_HEADER_AccessContorlAllowOrigin, RESPONSE_HEADER_CORS)
		.header(RESPONSE_HEADER_ContentType, RESPONSE_HEADER_ERROR).header(RESPONSE_HEADER_ERROR_CODE, errCode)
		.header(RESPONSE_HEADER_ERROR_MESSAGE, message);
	switch (errCode) {
	case NOT_FOUND_CODE:
	    r.status(Status.NOT_FOUND);
	    break;
	case INVALID_TEMPLATE_VERSION:
	case INVALID_CATEGORY:
	    r.status(Status.BAD_REQUEST);
	    break;
	case EXCEPTION_CODE:
	default:
	    r.status(Status.INTERNAL_SERVER_ERROR);
	    break;
	}
	return r.build();
    }

    private static List<String> filterMajorVersions(List<String> list, String defaultSchemaVersion) {
	List<String> output = new ArrayList<>();
	int n = list.size();
	// as the first version will be latest for the major version
	if (n > 0) {
	    output.add(list.get(0));
	}

	if (NtfUtils.DEFAULT_STR.equalsIgnoreCase(defaultSchemaVersion)) {
	    for (int i = 0; i < n - 1; i++) {
		int left = Integer.parseInt(list.get(i).split("\\.")[0]);
		int right = Integer.parseInt(list.get(i + 1).split("\\.")[0]);
		// if consecutive versions do not match
		if (left != right) {
		    output.add(list.get(i + 1));
		}
	    }
	}
	return output;
    }

}
