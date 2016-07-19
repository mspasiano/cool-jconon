package it.cnr.jconon.rest;

import it.cnr.cool.cmis.service.CMISService;
import it.cnr.cool.rest.Content;
import it.cnr.cool.rest.Page;
import it.cnr.cool.security.SecurityChecked;
import it.cnr.cool.security.service.UserService;
import it.cnr.cool.service.I18nService;
import it.cnr.jconon.service.call.CallService;
import it.cnr.jconon.util.DateUtils;
import it.cnr.jconon.util.StatoConvocazione;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.CookieParam;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.commons.exceptions.CmisUnauthorizedException;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Path("call")
@Component
@SecurityChecked(needExistingSession=true, checkrbac=false)
public class Call {
	private static final Logger LOGGER = LoggerFactory.getLogger(Call.class);
	@Autowired
	private CMISService cmisService;
	@Autowired
	private CallService callService;
	@Autowired
	private UserService userService;
	@Autowired
	private Content content;
	
	@GET
	@Path("download-xls")
	@Produces("application/vnd.ms-excel")
	public Response downloadFile(@Context HttpServletRequest req, @QueryParam("objectId") String objectId, @QueryParam("fileName") String fileName) throws IOException{
		LOGGER.debug("Download excel file with objectId:" + objectId);
		ResponseBuilder rb;
        Session session = cmisService.getCurrentCMISSession(req);
		try {
			Document doc = (Document) session.getObject(objectId);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			IOUtils.copy(doc.getContentStream().getStream(), out);
			rb = Response.ok(out.toByteArray());
			rb.header("Content-Disposition",
					"attachment; filename=\"" + fileName.concat(".xls\""));
			doc.delete();
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
			rb = Response.status(Status.INTERNAL_SERVER_ERROR);
		}
		return rb.build();
	}
	
	@GET
	@Path("applications.xls")
	@Produces(MediaType.APPLICATION_JSON)
	public Response extractionApplication(@Context HttpServletRequest req, @QueryParam("q") String query) throws IOException{
		LOGGER.debug("Extraction application from query:" + query);
		ResponseBuilder rb;
        Session session = cmisService.getCurrentCMISSession(req);
		try {
			String objectId = callService.extractionApplication(session, query, getContextURL(req), cmisService.getCMISUserFromSession(req).getId());
			Map<String, Object> model = new HashMap<String, Object>();
			model.put("objectId", objectId);
			model.put("fileName", "domande");
			rb = Response.ok(model);
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
			rb = Response.status(Status.INTERNAL_SERVER_ERROR);
		}
		return rb.build();
	}	

	@GET
	@Path("applications-single-call.xls")
	@Produces(MediaType.APPLICATION_JSON)
	public Response extractionApplicationForSingleCall(@Context HttpServletRequest req, @QueryParam("q") String query) throws IOException{
		LOGGER.debug("Extraction application from query:" + query);
		ResponseBuilder rb;
        Session session = cmisService.getCurrentCMISSession(req);
		try {
			Map<String, Object> model = callService.extractionApplicationForSingleCall(session, query, getContextURL(req), cmisService.getCMISUserFromSession(req).getId());
			String fileName = "domande";
			if(model.containsKey("nameBando")) {
				fileName = ((String) model.get("nameBando"));
				fileName = refactoringFileName(fileName, "");
			}
			model.put("fileName", fileName);
			rb = Response.ok(model);
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
			rb = Response.status(Status.INTERNAL_SERVER_ERROR);
		}
		return rb.build();
	}	

	@POST
	@Path("convocazioni")
	@Produces(MediaType.APPLICATION_JSON)
	public Response convocazioni(@Context HttpServletRequest request, @CookieParam("__lang") String lang, 
			@FormParam("callId") String callId, @FormParam("tipoSelezione")String tipoSelezione, @FormParam("luogo")String luogo, @FormParam("data")String data, 
			@FormParam("note")String note, @FormParam("firma")String firma, @FormParam("numeroConvocazione")String numeroConvocazione) throws IOException{
		ResponseBuilder rb;
		try {
			Session session = cmisService.getCurrentCMISSession(request);
			Long numConvocazioni = callService.convocazioni(session, cmisService.getCurrentBindingSession(request), 
					getContextURL(request), I18nService.getLocale(request, lang), cmisService.getCMISUserFromSession(request).getId(), 
					callId, tipoSelezione, luogo, DateUtils.parse(data), note, firma,  Optional.ofNullable(numeroConvocazione).map(map -> Integer.valueOf(map.toString())).orElse(1));
			rb = Response.ok(Collections.singletonMap("numConvocazioni", numConvocazioni));
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
			rb = Response.status(Status.INTERNAL_SERVER_ERROR);
		}
		return rb.build();
	}	

	@POST
	@Path("firma-convocazioni")
	@Produces(MediaType.APPLICATION_JSON)
	public Response firmaConvocazioni(@Context HttpServletRequest req, @FormParam("query") String query, @FormParam("userName")String userName, 
			@FormParam("password")String password, @FormParam("otp")String otp, @FormParam("firma")String firma) throws IOException{
		LOGGER.debug("Firma convocazioni from query:" + query);
		ResponseBuilder rb;
        Session session = cmisService.getCurrentCMISSession(req);
		try {
			Long numConvocazioni =  callService.firmaConvocazioni(session, cmisService.getCurrentBindingSession(req), query, getContextURL(req), cmisService.getCMISUserFromSession(req).getId(),
					 userName, password, otp, firma);
			rb = Response.ok(Collections.singletonMap("numConvocazioni", numConvocazioni));
		} catch (IOException e) {
			LOGGER.error(e.getMessage(), e);
			rb = Response.status(Status.INTERNAL_SERVER_ERROR);
		}
		return rb.build();
	}	

	@POST
	@Path("invia-convocazioni")
	@Produces(MediaType.APPLICATION_JSON)
	public Response inviaConvocazioni(@Context HttpServletRequest req, @FormParam("query") String query, @FormParam("callId")String callId, 
			@FormParam("userNamePEC")String userName, @FormParam("passwordPEC")String password) throws IOException{
		LOGGER.debug("Invia convocazioni from query:" + query);
		ResponseBuilder rb;
        Session session = cmisService.getCurrentCMISSession(req);
		try {
			Long numConvocazioni =  callService.inviaConvocazioni(session, cmisService.getCurrentBindingSession(req), query, getContextURL(req), cmisService.getCMISUserFromSession(req).getId(),
					callId, userName, password);
			rb = Response.ok(Collections.singletonMap("numConvocazioni", numConvocazioni));
		} catch (IOException e) {
			LOGGER.error(e.getMessage(), e);
			rb = Response.status(Status.INTERNAL_SERVER_ERROR);
		}
		return rb.build();
	}	
	
	@GET
	@Path("convocazione")
	public Response content(@Context HttpServletRequest req, @Context HttpServletResponse res, @QueryParam("nodeRef") String nodeRef) throws URISyntaxException {
		content.content(req, res, null, nodeRef, false, null);
    	Map<String, Object> properties = new HashMap<String, Object>();
    	properties.put("jconon_convocazione:stato", StatoConvocazione.RICEVUTO.name());		
		cmisService.createAdminSession().getObject(nodeRef).updateProperties(properties);
		return Response.ok().build();
	}
	
	//replace caratteri che non possono comparire nel nome del file in windows
	public static String refactoringFileName(String fileName, String newString) {
		return fileName.replaceAll("[“”\"\\/:*<>| ’']", newString).replace("\\", newString);
	}

	public String getContextURL(HttpServletRequest req) {
		return req.getScheme() + "://" + req.getServerName() + ":"
				+ req.getServerPort() + req.getContextPath();
	}
}