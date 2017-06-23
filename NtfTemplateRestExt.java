package com.ericsson.ntf.ext.webservices;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.bss.cil.cdal.ntf.Interface.ReadFrom;
import com.ericsson.bss.cil.cdal.ntf.service.domain.NotificationDomain.Notification;
import com.ericsson.bss.cil.cdal.ntf.service.domain.TemplateExtDomain.TemplateBase;
import com.ericsson.bss.cil.cdal.ntf.service.domain.TemplateExtDomain.Template_v1;
import com.ericsson.bss.cil.cdal.ntf.service.domain.TemplateExtDomain.Template_v2;
import com.ericsson.bss.cil.cdal.ntf.service.domain.TemplateExtDomain.Template_v3;
import com.ericsson.bss.cil.cdal.ntf.service.domain.TemplateIdDomain.Template;
import com.ericsson.ntf.common.util.NtfUtils;
import com.ericsson.ntf.configAgent.DataAccessEnablerInterface;

/**
 * This class is the REST interface to be used by the external PAC's . It supports only the READ
 * operation to get the list of templates for a particular PAC, a subset of PAC's or all PAC's
 * 
 * @author eashapr
 * 
 */

@Path("/ntf-rest/ntf/template")
public class NtfTemplateRestExt {

    private static final Logger LOG = LoggerFactory.getLogger(NtfTemplateRestExt.class);

    @Context
    UriInfo uriInfo;
    @Context
    HttpServletRequest request;

    @Context
    HttpServletResponse response;

    private DataAccessEnablerInterface daeIntf;

    // The following header is optional in the request. If not specified, the JSON object of the
    // version 1_0_0 will be returned in the response content.

    // Accept application/json;profile="http://ericsson.com/bss.ntf.templateEnquiry.1_0_0.json#"
    // JSON object of the specified version will only be returned as the response content.

    // Version 1.0 will return template id+keywords
    protected static final String VERSION_1 = "v1";
    // Version 2.0 will return template id + category
    protected static final String VERSION_2 = "v2";

    // Version 3 will return template id+ category+keywords
    protected static final String VERSION_3 = "v3";

    // Header in the Request
    protected static final String REQUEST_HEADER_NAME = "Accept";

    // Header in the Response
    protected static final String RESPONSE_HEADER_NAME = "Content-Type";
    protected static final String RESPONSE_HEADER_NAME1 = "Access-Control-Allow-Origin";
    protected static final String RESPONSE_HEADER_SUCCESS_v4 = "*";

    // Value in Response header for an error scenario
    protected static final String RESPONSE_HEADER_ERROR = "application/json; charset=utf-8; profile=http://ericsson.com/bss.ntf.errorSchema.1_0_0.json#";
    // Value in Response header for a success scenario
    protected static final String RESPONSE_HEADER_SUCCESS = "application/json; charset=utf-8; profile=http://ericsson.com/bss.ntf.templateEnquiry.1_0_0.json#";
    protected static final String RESPONSE_HEADER_SUCCESS_v2 = "application/json; charset=utf-8; profile=http://ericsson.com/bss.ntf.templateEnquiry.2_0_0.json#";
    protected static final String RESPONSE_HEADER_SUCCESS_v3 = "application/json; charset=utf-8; profile=http://ericsson.com/bss.ntf.templateEnquiry.3_0_0.json#";

    protected static final String NOT_FOUND_CODE = "ntf.templateEnquiry.pacIdNotFound";
    protected static final String EXCEPTION_CODE = "ntf.templateEnquiry.Exception";

    public NtfTemplateRestExt(DataAccessEnablerInterface dae) {
	daeIntf = dae;
    }

    /**
     * Method to get the templates for a given PAC/all PAC's
     * 
     * @param idParam
     *            - id of a single PAC, comma separated list of PAC ids or '*' indicating all PAC's
     * @return - List of template objects which satisfy the request
     * @throws IOException
     */
    @GET
    @Produces("application/json")
    // Defines that the next path parameter after template is
    // treated as a parameter
    // Allows to type http://localhost:8080/ntf-rest/ntf/template/CHA
    // CHA will be treated as parameter id passed as id
    @Path("/{id}")
    public Object getData(@PathParam("id") String idParam) throws IOException {

	LOG.debug("In getData id = {}", idParam);

	if (idParam.equalsIgnoreCase(VERSION_2) || idParam.equalsIgnoreCase(VERSION_3)) {
	    // New interface with version as part of the path...

	    String domainParam = request.getParameter("domain");
	    String categoryParam = request.getParameter("category");

	    LOG.debug("In getData domain = {}, category= {}", idParam, categoryParam);

	    return getTemplateData(idParam, domainParam, categoryParam);
	}
	return getTemplateData(null, idParam, null);
    }

    @OPTIONS
    @Path("/{id}")
    public Response getOptions() {
	return Response.ok().header("Access-Control-Allow-Origin", "*")
		.header("Access-Control-Allow-Methods", "GET, OPTIONS")
		.header("Access-Control-Allow-Headers", "Content-Type, Accept, X-Requested-With").build();
    }

    /**
     * Method to return the list of template data
     * 
     * @param versionParam
     *            version of the template details to be returned in the response
     * @param idParam
     *            - App id or comma separated list of TPG app ids
     * @param categoryParam
     *            - category to filter on
     * @return list of template data for the given TPG(s) and category
     * @throws IOException
     */
    private Object getTemplateData(String versionParam, String idParam, String categoryParam) throws IOException {

	LOG.debug("In getData id = {}", idParam);
	// Split the id - since it may be comma separated
	String[] idArr = idParam.split(",");

	try {

	    if (idArr != null && idArr.length > 0) {
		ArrayList<String> idList = new ArrayList<>();
		idList.addAll(Arrays.asList(idArr));
		List<Template> tempList;
		Object retRows = null;

		if (idList.contains("*")) {

		    tempList = daeIntf.getAllTemplate(ReadFrom.SERVER);
		    if (tempList != null && tempList.size() > 0) {
			retRows = getRowsFromTemplate(tempList, categoryParam, versionParam);
		    }
		} else {

		    tempList = new ArrayList<>();
		    for (String appId : idArr) {
			List<Template> templateForApp = daeIntf.getTemplateForApp(appId);
			if (templateForApp != null && templateForApp.size() > 0)
			    tempList.addAll(templateForApp);
		    }
		    if (tempList.size() > 0) {
			retRows = getRowsFromTemplate(tempList, categoryParam, versionParam);
		    }
		}
		if (retRows != null && ((List<Object>) retRows).size() > 0) {
		    return retRows;
		}
		String detailMsg = "pacId:" + idParam + " not found.";

		setErrorHeader();

		return setErrorResponse(HttpServletResponse.SC_NOT_FOUND, NOT_FOUND_CODE, "PAC not found.", detailMsg);
	    }

	} catch (Exception e) {
	    LOG.error("Exception in getting the Template list for id {}", idParam);
	    // Instead of throwing the entire exception, only send the error message

	    setErrorHeader();
	    return setErrorResponse(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, EXCEPTION_CODE, e.getMessage(),
		    "Exception in getting the Template list for id :" + idParam);

	}

	return null;
    }

    private ArrayList<TemplateBase> getRowsFromTemplate(List<Template> templateList, String category, String version)
	    throws Exception

    {
	LOG.info("getRowsFromTemplate invoked with category = {} , verison = {} for template size = {}", category,
		version, templateList.size());

	ArrayList<TemplateBase> retList = new ArrayList<>();

	List<Notification> allNotification = daeIntf.getAllNotification(ReadFrom.SERVER);

	for (Template template : templateList) {

	    try {
		String templateId = NtfUtils.safeToString(template.getTemplateId());
		String templateName = NtfUtils.safeToString(template.getTemplateName());
		String pacId = NtfUtils.safeToString(template.getAppId());
		String description = NtfUtils.safeToString(template.getDescription());
		String templateCategory = "";

		for (Notification notification : allNotification) {
		    if (NtfUtils.safeEqualsString(notification.getTemplateId(), templateId)) {
			templateCategory = notification.getCategory() == null ? ""
				: notification.getCategory().toString();
			break;
		    }
		}

		LOG.info("Found Template Category = {}", templateCategory);

		TemplateBase row = new TemplateBase();

		if (version != null && version.equalsIgnoreCase(VERSION_2)) {
		    row = new Template_v2();
		    ((Template_v2) row).setCategory(templateCategory);
		} else if (version != null && version.equalsIgnoreCase(VERSION_3)) {
		    row = new Template_v3();
		    ((Template_v3) row).setCategory(templateCategory);
		} else {
		    row = new Template_v1();
		}

		row.setTemplateId(templateId);
		row.setPacId(pacId);
		row.setTemplateName(templateName);
		row.setDescription(description);

		if (category != null && version != null) {
		    if (version.equalsIgnoreCase(VERSION_2)) {
			if (category.equalsIgnoreCase(((Template_v2) row).getCategory())) {
			    retList.add(row);
			}
		    } else if (version.equalsIgnoreCase(VERSION_3)) {
			if (category.equalsIgnoreCase(((Template_v3) row).getCategory())) {
			    retList.add(row);
			}
		    }
		} else {
		    retList.add(row);
		}
	    } catch (Exception e) {
		LOG.error("Exception in retrieving template: {}", template.getTemplateId(), e);
		throw e;
	    }
	}
	return retList;
    }

    /**
     * Sets the header for an error scenario
     */
    private void setErrorHeader() {
	response.setHeader(RESPONSE_HEADER_NAME, RESPONSE_HEADER_ERROR);
    }

    /**
     * Sets the response object for an error scenario
     */
    private static ErrorResponse setErrorResponse(int statusCode, String errCode, String message, String details) {
	ErrorResponse err = new ErrorResponse();
	err.setStatus(statusCode);
	err.setMessage(message);
	err.setCode(errCode);
	err.setDetails(details);
	return err;
    }
}
