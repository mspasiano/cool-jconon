package it.cnr.jconon.service;

import it.cnr.bulkinfo.BulkInfo;
import it.cnr.bulkinfo.BulkInfoImpl.FieldProperty;
import it.cnr.bulkinfo.BulkInfoImpl.FieldPropertySet;
import it.cnr.cool.cmis.service.CMISService;
import it.cnr.cool.cmis.service.NodeVersionService;
import it.cnr.cool.exception.CoolUserFactoryException;
import it.cnr.cool.mail.MailService;
import it.cnr.cool.mail.model.AttachmentBean;
import it.cnr.cool.mail.model.EmailMessage;
import it.cnr.cool.rest.util.Util;
import it.cnr.cool.security.service.UserService;
import it.cnr.cool.security.service.impl.alfresco.CMISUser;
import it.cnr.cool.service.BulkInfoCoolService;
import it.cnr.cool.service.I18nService;
import it.cnr.cool.service.JMSService;
import it.cnr.cool.util.Pair;
import it.cnr.cool.util.StringUtil;
import it.cnr.cool.web.scripts.exception.CMISApplicationException;
import it.cnr.cool.web.scripts.exception.ClientMessageException;
import it.cnr.jconon.cmis.model.JCONONDocumentType;
import it.cnr.jconon.cmis.model.JCONONPolicyType;
import it.cnr.jconon.cmis.model.JCONONPropertyIds;
import it.cnr.jconon.cmis.model.JCONONRelationshipType;
import it.cnr.jconon.model.ApplicationModel;
import it.cnr.jconon.model.PrintDetailBulk;
import it.cnr.jconon.service.cache.ApplicationAttachmentChildService;
import it.cnr.jconon.service.call.CallService;
import it.cnr.jconon.util.QrCodeUtil;
import it.spasia.opencmis.criteria.Criteria;
import it.spasia.opencmis.criteria.CriteriaFactory;
import it.spasia.opencmis.criteria.Order;
import it.spasia.opencmis.criteria.restrictions.Restrictions;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;

import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRExporterParameter;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JsonDataSource;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.engine.export.JRPdfExporterParameter;
import net.sf.jasperreports.engine.export.JRXlsExporter;
import net.sf.jasperreports.engine.fill.JRGzipVirtualizer;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimpleXlsReportConfiguration;

import org.apache.chemistry.opencmis.client.api.CmisObject;
import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.client.api.ItemIterable;
import org.apache.chemistry.opencmis.client.api.ObjectType;
import org.apache.chemistry.opencmis.client.api.OperationContext;
import org.apache.chemistry.opencmis.client.api.Property;
import org.apache.chemistry.opencmis.client.api.QueryResult;
import org.apache.chemistry.opencmis.client.api.Relationship;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.client.runtime.OperationContextImpl;
import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.commons.definitions.PropertyBooleanDefinition;
import org.apache.chemistry.opencmis.commons.definitions.PropertyDateTimeDefinition;
import org.apache.chemistry.opencmis.commons.definitions.PropertyDecimalDefinition;
import org.apache.chemistry.opencmis.commons.definitions.PropertyDefinition;
import org.apache.chemistry.opencmis.commons.enums.Cardinality;
import org.apache.chemistry.opencmis.commons.enums.IncludeRelationships;
import org.apache.chemistry.opencmis.commons.enums.VersioningState;
import org.apache.chemistry.opencmis.commons.exceptions.CmisObjectNotFoundException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisStreamNotSupportedException;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.ContentStreamImpl;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.format.number.NumberFormatter;
import org.springframework.util.StringUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class PrintService {
	private static final Logger LOGGER = LoggerFactory.getLogger(PrintService.class);
	@Autowired
	private CMISService cmisService;
	@Autowired
	private UserService userService;
	@Autowired
	private I18nService i18nService;
	@Autowired
	private MailService mailService;
	@Autowired
	private NodeVersionService nodeVersionService;	
	@Autowired
	private BulkInfoCoolService bulkInfoService;	
	@Autowired
	private CallService callService;
	
	@Autowired	
	private ApplicationContext context;

    @Autowired
    private TypeService typeService;
	
    @Autowired
    private ApplicationAttachmentChildService jsonlistApplicationNoAspectsForeign;
    @Autowired
    private ApplicationAttachmentChildService jsonlistApplicationNoAspectsItalian;
    
	public void printApplication(JMSService jmsQueue, String nodeRef, final String contextURL, final Locale locale, final boolean email) {
		jmsQueue.sendRecvAsync(nodeRef, new MessageListener() {
			@Override
			public void onMessage(Message arg0) {
				String nodeRef = null;
				try {
					ObjectMessage objMessage = (ObjectMessage)arg0;
					nodeRef = (String) objMessage.getObject();
					LOGGER.info("Start print application width id: " + nodeRef);

					Session cmisSession = cmisService.createAdminSession();
					Folder application = (Folder) cmisSession.getObject(nodeRef);
					Folder call = (Folder) cmisSession.getObject(application.getParentId());
					application.refresh();
					CMISUser applicationUser;
					try {
						applicationUser = userService.loadUserForConfirm(
								(String)application.getPropertyValue(JCONONPropertyIds.APPLICATION_USER.value()));
					} catch (CoolUserFactoryException e) {
						throw new ClientMessageException("User not found");
					}
					String nameRicevutaReportModel = getNameRicevutaReportModel(cmisSession, application);
					byte[] stampaByte = getRicevutaReportModel(cmisSession,
							application, contextURL, locale, nameRicevutaReportModel);
					InputStream is = new ByteArrayInputStream(stampaByte);
					archiviaRicevutaReportModel(cmisSession, application, is, nameRicevutaReportModel, false);
					/**
					 * Spedisco la mail con la stampa allegata
					 */
					if (email) {
						Map<String, Object> mailModel = new HashMap<String, Object>();
						mailModel.put("contextURL", contextURL);
						mailModel.put("message", context.getBean("messageMethod", locale));
						mailModel.put("folder", application);
						mailModel.put("call", call);
						String body = Util.processTemplate(mailModel, "/pages/application/application.print.html.ftl");
						EmailMessage message = new EmailMessage();
						List<String> emailList = new ArrayList<String>(), emailBccList = new ArrayList<String>();
						emailList.add(applicationUser.getEmail());
						mailModel.put("email_comunicazione", applicationUser.getEmail());
						message.setRecipients(emailList);
						if (emailBccList.isEmpty()) {
							message.setBccRecipients(emailBccList);
						}
						message.setSubject("[concorsi] " + i18nService.getLabel("subject-print-domanda", locale, call.getPropertyValue(JCONONPropertyIds.CALL_CODICE.value())));
						message.setBody(body);

						message.setAttachments(Arrays.asList(new AttachmentBean(nameRicevutaReportModel, stampaByte)));
						mailService.send(message);						
					}
					if (LOGGER.isInfoEnabled())
						LOGGER.info("End print application width id: " + nodeRef);
				} catch(Exception t) {
					LOGGER.error("Error while print application width id:" + nodeRef, t);
				}
			}
		});
	}

	public String getNameRicevutaReportModel(Session cmisSession, Folder application) throws CMISApplicationException {
		String shortNameEnte = "CNR";
		Folder call = (Folder) cmisSession.getObject(application.getParentId());
		DateFormat formatter = new SimpleDateFormat("yyyy/MM/dd");
		GregorianCalendar dataDomanda = application.getPropertyValue(JCONONPropertyIds.APPLICATION_DATA_DOMANDA.value());
		String dataApplication = "PROVVISORIA";
		if (dataDomanda != null)
			dataApplication = formatter.format(dataDomanda.getTime()).replace("/", "_");

		return shortNameEnte +
				"-" +
				call.getPropertyValue(JCONONPropertyIds.CALL_CODICE.value())+
				"-RD-" +
				application.getPropertyValue(JCONONPropertyIds.APPLICATION_USER.value())+
				"-" +
				dataApplication +
				".pdf";
	}
	
	@SuppressWarnings({ "unchecked", "deprecation" })
	public byte[] getRicevutaReportModel(Session cmisSession, Folder application, String contextURL, Locale locale, String nameRicevutaReportModel)
					throws CMISApplicationException {
		Folder call = application.getFolderParent();
		Properties props = i18nService.loadLabels(locale);
		props.putAll(callService.getDynamicLabels(call, cmisSession));
		ApplicationModel applicationModel = new ApplicationModel(application,
				cmisSession.getDefaultContext(),
				props, contextURL);
		try {
			CMISUser applicationUser = userService.loadUserForConfirm((String)application.getPropertyValue(JCONONPropertyIds.APPLICATION_USER.value()));
			applicationModel.getProperties().put("jasperReport:user_matricola", applicationUser.getMatricola());
			applicationModel.getProperties().put("jasperReport:user_email_comunicazione", applicationUser.getEmail());
		} catch (CoolUserFactoryException e) {
			LOGGER.error("User not found", e);
		}

		final Gson gson = new GsonBuilder()
		.setDateFormat("yyyy-MM-dd'T'HH:mm:ss")
		.excludeFieldsWithoutExposeAnnotation()
		.registerTypeAdapter(GregorianCalendar.class, new JsonSerializer<GregorianCalendar>() {
			@Override
			public JsonElement serialize(GregorianCalendar src, Type typeOfSrc,
					JsonSerializationContext context) {
				return  context.serialize(src.getTime());
			}
		}).create();
		if (call.getPropertyValue(JCONONPropertyIds.CALL_ELENCO_ASSOCIATIONS
				.value()) != null) {
			applicationModel
					.getProperties()
					.put("allegati",
							getAllegati(
									application,
									JCONONPolicyType.JCONON_ATTACHMENT_GENERIC_DOCUMENT,
									cmisSession, applicationModel));
		}

		if (call.getPropertyValue(JCONONPropertyIds.CALL_ELENCO_SEZIONE_CURRICULUM.value()) != null) {
			applicationModel.getProperties().put("curriculum", getCurriculum(
					(List<String>) call
					.getPropertyValue(JCONONPropertyIds.CALL_ELENCO_SEZIONE_CURRICULUM
							.value()),
							application, cmisSession, applicationModel));
		}
		if (call.getPropertyValue(JCONONPropertyIds.CALL_ELENCO_SEZIONE_PRODOTTI.value()) != null) {
			applicationModel.getProperties().put("prodotti", getProdotti(
					(List<String>)call.getPropertyValue(JCONONPropertyIds.CALL_ELENCO_SEZIONE_PRODOTTI.value()),
					application, JCONONPolicyType.PEOPLE_NO_SELECTED_PRODUCT, cmisSession, applicationModel));
			applicationModel.getProperties().put("prodottiScelti", getProdotti(
					(List<String>)call.getPropertyValue(JCONONPropertyIds.CALL_ELENCO_SEZIONE_PRODOTTI.value()),
					application, JCONONPolicyType.PEOPLE_SELECTED_PRODUCT, cmisSession, applicationModel));
		}
		if (call.getPropertyValue(JCONONPropertyIds.CALL_ELENCO_SEZIONE_SCHEDE_ANONIME.value()) != null) {
			applicationModel.getProperties().put("schedeAnonime", getCurriculum(
					(List<String>) call
					.getPropertyValue(JCONONPropertyIds.CALL_ELENCO_SEZIONE_SCHEDE_ANONIME
							.value()),
							application, cmisSession, applicationModel));
		}		
		applicationModel.getProperties().put("dichiarazioni", 
				getDichiarazioni(
						bulkInfoService.find(application.getType().getId().replace(":", "_")),
						application,
						JCONONPropertyIds.CALL_ELENCO_ASPECTS,
						applicationModel));
		applicationModel.getProperties().put("datiCNR", getDichiarazioni(
				bulkInfoService.find(application.getType().getId().replace(":", "_")),
				application,
				JCONONPropertyIds.CALL_ELENCO_ASPECTS_SEZIONE_CNR,
				applicationModel));
		applicationModel.getProperties().put("ulterioriDati", getDichiarazioni(
				bulkInfoService.find(application.getType().getId().replace(":", "_")),
				application,
				JCONONPropertyIds.CALL_ELENCO_ASPECTS_ULTERIORI_DATI,
				applicationModel));
		String labelSottoscritto = i18nService.getLabel(
				"application.text.sottoscritto.lower." + application.getPropertyValue(JCONONPropertyIds.APPLICATION_SESSO.value()), locale);
		
		String labelSanzioniPenali = 
				i18nService.getLabel("text.jconon_application_dichiarazione_sanzioni_penali_" + call.getPropertyValue(JCONONPropertyIds.CALL_CODICE.value()), locale);
		if (labelSanzioniPenali == null)
			labelSanzioniPenali = i18nService.getLabel("text.jconon_application_dichiarazione_sanzioni_penali", locale, labelSottoscritto);
		else
			labelSanzioniPenali = i18nService.getLabel("text.jconon_application_dichiarazione_sanzioni_penali_" + call.getPropertyValue(JCONONPropertyIds.CALL_CODICE.value()), locale, labelSottoscritto);
		
		applicationModel.getProperties().put("label_jconon_application_dichiarazione_sanzioni_penali", labelSanzioniPenali);
		applicationModel.getProperties().put("label_jconon_application_dichiarazione_dati_personali", 
				i18nService.getLabel("text.jconon_application_dichiarazione_dati_personali", locale, labelSottoscritto));
		
		String json = "{\"properties\":"+gson.toJson(applicationModel.getProperties())+"}";

		try {
			/**
			 * Calcolo il QRCODE del link alla stampa
			 */
			ByteArrayOutputStream qrcode = QrCodeUtil.getQrcode(contextURL + "/rest/content?path=" + application.getPath() + "/" + nameRicevutaReportModel);

			Map<String, Object> parameters = new HashMap<String, Object>();
			JRDataSource datasource = new JsonDataSource(new ByteArrayInputStream(json.getBytes(Charset.forName("UTF-8"))), "properties");
			JRGzipVirtualizer vir = new JRGzipVirtualizer(100);
			final ResourceBundle resourceBundle = ResourceBundle.getBundle(
					"net.sf.jasperreports.view.viewer", locale);
			parameters.put(JRParameter.REPORT_LOCALE, locale);
			parameters.put(JRParameter.REPORT_RESOURCE_BUNDLE, resourceBundle);
			parameters.put(JRParameter.REPORT_DATA_SOURCE, datasource);
			parameters.put(JRParameter.REPORT_VIRTUALIZER, vir);
			parameters.put("DIR_IMAGE", this.getClass().getResource("/it/cnr/jconon/print/").getPath());
			parameters.put("SUBREPORT_DIR", this.getClass().getResource("/it/cnr/jconon/print/").getPath());

			if (qrcode != null) {
				parameters.put("QRCODE", new ByteArrayInputStream(qrcode.toByteArray()));
			}
			ClassLoader classLoader = ClassLoader.getSystemClassLoader();
			parameters.put(JRParameter.REPORT_CLASS_LOADER, classLoader);

			JasperPrint jasperPrint = JasperFillManager.fillReport(this.getClass().getResourceAsStream("/it/cnr/jconon/print/DomandaConcorso.jasper"), parameters);


			ByteArrayOutputStream os = new ByteArrayOutputStream();
			JRPdfExporter exporter = new JRPdfExporter();
			exporter.setParameter(JRExporterParameter.JASPER_PRINT, jasperPrint);
			exporter.setParameter(JRExporterParameter.OUTPUT_STREAM, os);
			exporter.setParameter(JRPdfExporterParameter.FORCE_LINEBREAK_POLICY, Boolean.TRUE);
			exporter.exportReport();
			return os.toByteArray();
		} catch (Exception e) {
			throw new CMISApplicationException("Error in JASPER", e);
		}
	}
	public String archiviaRicevutaReportModel(Folder application, InputStream is, String nameRicevutaReportModel, boolean confermata) throws CMISApplicationException {
		Session cmisSession = cmisService.createAdminSession();
		return archiviaRicevutaReportModel(cmisSession, application, is, nameRicevutaReportModel, confermata);
	}

	public String archiviaRicevutaReportModel(Session cmisSession, Folder application,
			InputStream is, String nameRicevutaReportModel, boolean confermata) throws CMISApplicationException {
		try {
			ContentStream contentStream = new ContentStreamImpl(nameRicevutaReportModel,
					BigInteger.valueOf(is.available()),
					"application/pdf",
					is);
			Map<String, Object> properties = new HashMap<String, Object>();
			properties.put(PropertyIds.OBJECT_TYPE_ID, JCONONDocumentType.JCONON_ATTACHMENT_APPLICATION.value());
			properties.put(PropertyIds.NAME, nameRicevutaReportModel);
			String docId = findRicevutaApplicationId(cmisSession, application);
			if (docId!=null) {
				try{
					Document doc = (Document) cmisSession.getObject(docId);
					if (confermata) {					
						int pointPosition = nameRicevutaReportModel.lastIndexOf('.');
						nameRicevutaReportModel = nameRicevutaReportModel.substring(0, pointPosition).
								concat("-").concat(doc.getVersionLabel()).concat(".pdf");
						properties.put(PropertyIds.NAME, nameRicevutaReportModel);
						doc = cmisSession.getLatestDocumentVersion(doc.updateProperties(properties, true));
					}
					doc.setContentStream(contentStream, true, true);
					doc = doc.getObjectOfLatestVersion(false);
					docId = doc.getId();
				}catch (CmisObjectNotFoundException e) {
					docId = createApplicationDocument(application, contentStream, properties);
				}catch(CmisStreamNotSupportedException ex) {
					LOGGER.error("Cannot set Content Stream on id:"+ docId + " ------" + ex.getErrorContent(), ex);
					throw ex;
				}
			} else {
				docId = createApplicationDocument(application, contentStream, properties);
			}
			return docId;
		} catch (Exception e) {
			throw new CMISApplicationException("Error in JASPER", e);
		}
	}
	private String createApplicationDocument(Folder application, ContentStream contentStream, Map<String, Object> properties){
		Document doc = application.createDocument(properties, contentStream, VersioningState.MAJOR);
		nodeVersionService.addAutoVersion(doc, false);
		return doc.getId();
	}	
	
	public String findDocRiconoscimentoId(Session cmisSession, Folder source){
		return findAttachmentId(cmisSession, source, JCONONDocumentType.JCONON_ATTACHMENT_DOCUMENTO_RICONOSCIMENTO);
	}

	public String findCurriculumId(Session cmisSession, Folder source){
		return findAttachmentId(cmisSession, source, JCONONDocumentType.JCONON_ATTACHMENT_CURRICULUM_VITAE);
	}

	public String findRicevutaApplicationId(Session cmisSession, Folder source){
		return findAttachmentId(cmisSession, source, JCONONDocumentType.JCONON_ATTACHMENT_APPLICATION, false);
	}
	public String findAttachmentId(Session cmisSession, Folder source, JCONONDocumentType documentType){
		return findAttachmentId(cmisSession, source, documentType, true);
	}
	public String findAttachmentId(Session cmisSession, Folder source, JCONONDocumentType documentType, boolean search){
		if (search) {
			Criteria criteria = CriteriaFactory.createCriteria(documentType.queryName());
			criteria.addColumn(PropertyIds.OBJECT_ID);
			criteria.addColumn(PropertyIds.NAME);
			criteria.add(Restrictions.inFolder(source.getId()));
			ItemIterable<QueryResult> iterable = criteria.executeQuery(cmisSession, false, cmisSession.getDefaultContext());
			for (QueryResult queryResult : iterable) {
				return queryResult.getPropertyValueById(PropertyIds.OBJECT_ID);
			}			
		} else {
			for (CmisObject cmisObject : source.getChildren()) {
				if (cmisObject.getType().getId().equals(documentType.value()))
					return cmisObject.getId();
			}			
		}
		return null;
	}


	/**
	 * 1. Prendiamo tutte le associazioni della domanda (application)
	 * 2. Per ogni associazione prendiamo il corrispondente PrintForm dal BulkInfo
	 * (passato come parametro)
	 * 3. Per ogni fieldProperty del PrintForm si costruisce una riga(?) dell'output
	 *
	 * @param bulkInfo
	 * @param application
	 * @param callProperty
	 * @return
	 * 
	 */
	private List<PrintDetailBulk> getDichiarazioni(BulkInfo bulkInfo,
			Folder application, JCONONPropertyIds callProperty,
			ApplicationModel applicationModel){
		List<PrintDetailBulk> result = new ArrayList<PrintDetailBulk>();
		// Recupero il bando
		Folder call = application.getParents().get(0); // chi e' il parent?
		List<String> associations = call.getPropertyValue(callProperty.value());
		boolean isCittadinoItaliano = application.getPropertyValue(JCONONPropertyIds.APPLICATION_FL_CITTADINO_ITALIANO.value());
		if (isCittadinoItaliano) {
			associations.removeAll(jsonlistApplicationNoAspectsItalian.getTypes());
		} else {
			associations.removeAll(jsonlistApplicationNoAspectsForeign.getTypes());			
		}		
		for (int i = 0; i < associations.size(); i++) {
			String association = associations.get(i);			
			// immagino che questa logica serva anche da altre parti. Possiamo
			// considerare di spostarla in BulkInfoService, oppure direttamente
			// in BulkInfoImpl?
			FieldPropertySet printForm = bulkInfoService.find(association).getPrintForms().get(
					association);
			if (printForm != null) {
				String applicationValue = String.valueOf(application
						.getPropertyValue(printForm.getKey())); // ?? questa
																// dichiarazione
																// va spostata
																// nell'else del
																// seguente if
				FieldProperty fieldProperty = printForm
						.getFieldProperty(applicationValue); // ?? questa
																// dichiarazione
																// va spostata
																// nell'else del
																// seguente if
				PrintDetailBulk detail = new PrintDetailBulk();
				detail.setTitle(String.valueOf(
						Character.toChars(i + 65)[0]).concat(") "));
				if (printForm.getKey() == null) {
					printField(printForm, applicationModel, application, detail, bulkInfo);
				} else {
					String labelKey = fieldProperty != null ? fieldProperty.getAttribute("label") : null;
					if (application.getPropertyValue(printForm.getKey()) == null || fieldProperty == null || labelKey == null) 
						continue;
					detail.addField(new Pair<String, String>(null, formNameMessage(fieldProperty, bulkInfo, detail, applicationModel, application, labelKey)));
				}
				if (detail.getFields() != null && !detail.getFields().isEmpty())
					result.add(detail);
			}
		}
		return result;
	}

	private String formNameMessage (FieldProperty fieldProperty, BulkInfo bulkInfo, PrintDetailBulk detail, 
			ApplicationModel applicationModel, Folder application, String labelKey) {
		String message = "";
		if (fieldProperty.getAttribute("formName") != null) {
			List<Object> params = new ArrayList<Object>();
			FieldPropertySet printForm1 = bulkInfo.getPrintForms().get(fieldProperty.getAttribute("formName"));
			if (printForm1 != null && printForm1.getKey() != null && printForm1.getKey().equals("false")) {
				if (labelKey != null)
					detail.addField(new Pair<String, String>(null, applicationModel.getMessage(
							labelKey)));
				printField(printForm1, applicationModel, application, detail, bulkInfo);
			} else {
				for (FieldProperty paramFieldProperty : bulkInfo
						.getPrintForm(fieldProperty
								.getAttribute("formName"))) {
					Object param = applicationModel.getProperties()
							.get(paramFieldProperty
									.getAttribute("property"));
					if (param == null)
						param = application.getPropertyValue(paramFieldProperty
								.getAttribute("property"));
					if (param == null) 
						param = "";
					params.add(param);						
				}
				message = message.concat(applicationModel.getMessage(
						labelKey, params.toArray()));					
			}
		} else {
			message = message.concat(applicationModel
					.getMessage(labelKey));
		}
		return message;
	}
	
	private List<PrintDetailBulk> getAllegati(Folder application, JCONONPolicyType allegati,
			Session cmisSession, ApplicationModel applicationModel) {
		return getAllegati(application,
				allegati, cmisSession, applicationModel, true);
	}

	private List<PrintDetailBulk> getAllegati(Folder application, JCONONPolicyType allegati,
			Session cmisSession, ApplicationModel applicationModel,
			boolean printDetail) {

		List<PrintDetailBulk> result = new ArrayList<PrintDetailBulk>();
		Criteria criteria = CriteriaFactory
				.createCriteria(allegati.queryName());
		criteria.addColumn(PropertyIds.OBJECT_ID);
		criteria.add(Restrictions.inFolder(application.getId()));
		ItemIterable<QueryResult> queryResults = criteria.executeQuery(
				cmisSession, false, cmisSession.getDefaultContext());
		if (queryResults.getTotalNumItems() > 0) {
			for (QueryResult queryResult : queryResults
					.getPage(Integer.MAX_VALUE)) {
				Document riga = (Document) cmisSession
						.getObject((String) queryResult
								.getPropertyValueById(PropertyIds.OBJECT_ID));
				String link = null;
				if (((BigInteger) riga
						.getPropertyValue(PropertyIds.CONTENT_STREAM_LENGTH))
						.compareTo(BigInteger.ZERO) == 1) {
					link = applicationModel.getContextURL()
							+ "/search/content?nodeRef=" + riga.getId();
				}
				String type = applicationModel.getMessage(riga.getType()
						.getId());
				if (type.equals(riga.getType().getId()))
					type = riga.getType().getDisplayName();
				List<Pair<String, String>> detail;
				Pair<String, String> pairName = new Pair<String, String>(riga
						.getProperty(PropertyIds.NAME).getDisplayName(), riga
						.getProperty(PropertyIds.NAME).getValueAsString());
				if (printDetail) {
					detail = getFields(riga, applicationModel);
					if (!detail.contains(pairName))
						detail.add(pairName);
					result.add(new PrintDetailBulk(null, type, link, detail,
							null));
				} else {
					result.add(new PrintDetailBulk(null, type, link, riga
							.getProperty(PropertyIds.NAME).getValueAsString(),
							null));
				}
			}
		}
		return result;
	}

	private List<PrintDetailBulk> getCurriculum(List<String> propertyValue,
			Folder application, Session cmisSession,
			ApplicationModel applicationModel) {
		return getCurriculum(propertyValue, application,
				cmisSession, applicationModel, true);
	}

	private List<PrintDetailBulk> getCurriculum(List<String> propertyValue,
			Folder application, Session cmisSession,
			ApplicationModel applicationModel, boolean printDetail) {
		List<PrintDetailBulk> result = new ArrayList<PrintDetailBulk>();
		Map<String, List<Pair<String, String>>> sezioni = getSezioni(propertyValue, cmisSession);
		for (String key : sezioni.keySet()) {
			for (Pair<String, String> pair : sezioni.get(key)) {
				Criteria criteria = CriteriaFactory.createCriteria(pair
						.getSecond());
				criteria.addColumn(PropertyIds.OBJECT_ID);
				criteria.add(Restrictions.inFolder(application.getId()));
				addOrderCurriculum(cmisSession, pair.getSecond(), criteria);
				ItemIterable<QueryResult> queryResults = criteria.executeQuery(
						cmisSession, false, cmisSession.getDefaultContext());
				if (queryResults.getTotalNumItems() > 0) {
					for (QueryResult queryResult : queryResults
							.getPage(Integer.MAX_VALUE)) {
						CmisObject riga = cmisSession
								.getObject((String) queryResult
										.getPropertyValueById(PropertyIds.OBJECT_ID));
						if (printDetail) {
							result.add(new PrintDetailBulk(key,
									pair.getFirst(), null, getFields(riga,
											applicationModel), null));
						} else {
							String link = null;
							if (((BigInteger) riga
									.getPropertyValue(PropertyIds.CONTENT_STREAM_LENGTH))
									.compareTo(BigInteger.ZERO) == 1) {
								link = applicationModel.getContextURL()
										+ "/search/content?nodeRef="
										+ riga.getId() + "&fileName="+riga.getName()+".pdf";
							}
							String ruolo = riga
									.getPropertyValue("cvelement:altroRuoloProgetto");
							if (ruolo == null)
								ruolo = riga
								.getPropertyValue("cvelement:ruoloProgetto");
							if (ruolo == null)
								ruolo = riga
								.getPropertyValue("cvelement:altroRuoloIncarico");
							if (ruolo == null)
								ruolo = riga
								.getPropertyValue("cvelement:ruoloIncarico");
							if (ruolo != null){
								ruolo = ruolo.replace("_", " ");
								ruolo += " - ";
							} else {
								ruolo = "";
							}

							String title = riga
									.getPropertyValue("cvelement:denominazioneIncarico");
							if (title == null)
								title = riga
										.getPropertyValue("cvelement:denominazioneIstituto");
							if (title == null)
								title = riga
										.getPropertyValue("cvelement:titoloProgetto");
							if (title == null)
								title = riga
										.getPropertyValue("cvelement:denominazioneStruttura");
							if (title == null)
								title = riga
										.getPropertyValue("cvelement:rivista");
							if (title == null)
								title = riga
										.getPropertyValue("cvelement:tipologiaOrganismo");
							if (title == null)
								title = riga
										.getPropertyValue("cvelement:titoloEvento");
							if (title == null)
								title = riga
										.getPropertyValue("cvelement:descrizionePremio");
							if (riga.getPropertyValue("cvelement:attivitaSvolta") != null)
								title += " - "
										+ riga.getPropertyValue("cvelement:attivitaSvolta");
							if (riga.getPropertyValue("cvelement:descrizionePartecipazione") != null)
								title += " - "
										+ riga.getPropertyValue("cvelement:descrizionePartecipazione");
							PrintDetailBulk detail = new PrintDetailBulk(null,
									pair.getFirst(), link, ruolo + title, null);
							String periodo = "";
							SimpleDateFormat dateFormat = new SimpleDateFormat(
									"dd/MM/yyyy");
							if (riga.getPropertyValue("cvelement:periodAttivitaDal") != null)
								periodo += "Dal "
										+ dateFormat
												.format(((Calendar) riga
														.getPropertyValue("cvelement:periodAttivitaDal"))
														.getTime());
							if (riga.getPropertyValue("cvelement:periodAttivitaAl") != null)
								periodo += " Al "
										+ dateFormat
												.format(((Calendar) riga
														.getPropertyValue("cvelement:periodAttivitaAl"))
														.getTime());
							if (riga.getPropertyValue("cvelement:attivitainCorso") != null)
								periodo += " attivita in corso";
							if (riga.getPropertyValue("cvelement:oreComplessive") != null)
								periodo += " Ore complessive "
										+ ((BigDecimal) (riga
												.getPropertyValue("cvelement:oreComplessive"))).setScale(0,BigDecimal.ROUND_DOWN);

							detail.setPeriodo(periodo);
							result.add(detail);
						}
					}
				}
			}
		}
		return result;
	}

	private List<PrintDetailBulk> getProdotti(List<String> propertyValue,
			Folder application, JCONONPolicyType peopleProduct,
			Session cmisSession, ApplicationModel applicationModel) {
		return getProdotti(propertyValue, application,
				peopleProduct, cmisSession, applicationModel, true);
	}

	private List<PrintDetailBulk> getProdotti(List<String> propertyValue,
			Folder application, JCONONPolicyType peopleProduct,
			Session cmisSession, ApplicationModel applicationModel,
			boolean printDetail) {
		List<PrintDetailBulk> result = new ArrayList<PrintDetailBulk>();
		OperationContext ocRel = new OperationContextImpl(
				cmisSession.getDefaultContext());
		ocRel.setIncludeRelationships(IncludeRelationships.SOURCE);
		Map<String, List<Pair<String, String>>> sezioni = getSezioni(propertyValue, cmisSession);
		for (String key : sezioni.keySet()) {
			for (Pair<String, String> pair : sezioni.get(key)) {
				Criteria criteria = CriteriaFactory.createCriteria(pair
						.getSecond());
				Criteria criteriaAspect = criteria.createCriteria(
						peopleProduct.queryName(), "people");
				Criteria criteriaCommon = criteria.createCriteria(
						"cvpeople:commonMetadata", "common");
				criteriaAspect.addJoinCriterion(Restrictions.eqProperty(
						criteria.prefix(PropertyIds.OBJECT_ID),
						criteriaAspect.prefix(PropertyIds.OBJECT_ID)));
				criteriaCommon.addJoinCriterion(Restrictions.eqProperty(
						criteria.prefix(PropertyIds.OBJECT_ID),
						criteriaCommon.prefix(PropertyIds.OBJECT_ID)));
				criteria.addColumn(PropertyIds.OBJECT_ID);
				criteriaCommon.addOrder(Order.desc("common.cvpeople:anno"));
				criteria.add(Restrictions.inFolder(application.getId()));
				criteria.addOrder(Order.desc(PropertyIds.NAME));
				ItemIterable<QueryResult> queryResults = criteria.executeQuery(
						cmisSession, false, cmisSession.getDefaultContext());
				if (queryResults.getTotalNumItems() > 0) {
					for (QueryResult queryResult : queryResults
							.getPage(Integer.MAX_VALUE)) {
						List<PrintDetailBulk> rels = new ArrayList<PrintDetailBulk>();
						CmisObject riga = cmisSession
								.getObject(
										(String) queryResult
												.getPropertyValueById(PropertyIds.OBJECT_ID),
										ocRel);
						if (riga.getRelationships() != null
								&& !riga.getRelationships().isEmpty()) {
							for (Relationship relationship : riga
									.getRelationships()) {
								if (relationship
										.getType()
										.getId()
										.equals(JCONONRelationshipType.JCONON_ATTACHMENT_IN_PRODOTTO
												.value())) {
									CmisObject target = cmisSession
											.getObject(relationship.getTarget());
									String link = applicationModel
											.getContextURL()
											+ "/search/content?nodeRef="
											+ target.getId();
									if (printDetail)
										rels.add(new PrintDetailBulk(null,
												"Allegati", link, getFields(target,
														applicationModel), null));
									else {
										rels.add(new PrintDetailBulk(null,
												"Allegati", link,
												target.getProperty(
														PropertyIds.NAME)
														.getValueAsString(),
												null));
									}
								}
							}
						}
						if (printDetail) {
							result.add(new PrintDetailBulk(key,
									pair.getFirst(), null, getFields(riga,
											applicationModel), rels));
						} else {
							String link = null;
							if (((BigInteger) riga
									.getPropertyValue(PropertyIds.CONTENT_STREAM_LENGTH))
									.compareTo(BigInteger.ZERO) == 1) {
								link = applicationModel.getContextURL()
										+ "/search/content?nodeRef="
										+ riga.getId() + "&fileName="+riga.getName()+".pdf";
							}
							String title = riga
									.getPropertyValue("cvpeople:id_tipo_txt");
							title += " - "
									+ riga.getPropertyValue("cvpeople:titolo");

							PrintDetailBulk detail = new PrintDetailBulk(key,
									pair.getFirst(), link, title, rels);
							detail.setPeriodo(String.valueOf(riga
									.getPropertyValue("cvpeople:anno")));
							//Richieste di ampliamento della scheda di valutazione
							
							if (riga.getProperty("cvpeople:numeroCitazioni") != null
									&& riga.getProperty("cvpeople:numeroCitazioni")
											.getValues().size() != 0)
								detail.setNroCitazioni(((BigInteger) riga.getProperty(
										"cvpeople:numeroCitazioni")
										.getValue()).intValue());
							if (riga.getProperty("cvpeople:ifRivistaValore") != null
									&& riga.getProperty("cvpeople:ifRivistaValore")
											.getValues().size() != 0) {
								try {
									detail.setIfValore(riga.getProperty(
											"cvpeople:ifRivistaValore")
											.getValueAsString());
								} catch(NumberFormatException _ex) {
									LOGGER.error("Estrazione scheda di valutazione NumberFormatException for " + riga.getProperty(
											"cvpeople:ifRivistaValore")
											.getValueAsString() + " objectId:" + riga.getId());
								}
							}
							
							if (riga.getProperty("cvpeople:altroRuoloSvolto") != null
									&& riga.getProperty(
											"cvpeople:altroRuoloSvolto")
											.getValues().size() != 0) {
									detail.setRuolo(riga.getProperty(
											"cvpeople:altroRuoloSvolto")
											.getValueAsString());
							}
							if (riga.getProperty("cvpeople:ruoloSvolto") != null
									&& riga.getProperty("cvpeople:ruoloSvolto")
											.getValues().size() != 0){
								for (Object ruoloSvolto : riga.getProperty("cvpeople:ruoloSvolto").getValues()) {
									if (!ruoloSvolto.equals("Altro")) {
										if (detail.getRuolo() != null) {
											detail.setRuolo(String.valueOf(ruoloSvolto).replace("_", " ") + "," + detail.getRuolo());
										} else {
											detail.setRuolo(String.valueOf(ruoloSvolto).replace("_", " "));
										}
									}
								}
							}
							if (riga.getProperty("cvpeople:altroIfRivistaFonte") != null
									&& riga.getProperty(
											"cvpeople:altroIfRivistaFonte")
											.getValues().size() != 0) {
								detail.setIfFonte(riga.getProperty(
										"cvpeople:altroIfRivistaFonte")
										.getValueAsString());
							}							
							
							if (riga.getProperty("cvpeople:SjrQuartile") != null
									&& riga.getProperty("cvpeople:SjrQuartile")
											.getValues().size() != 0){
								detail.setQuartile(riga.getProperty("cvpeople:SjrQuartile").getValueAsString());
							}
							
							if (riga.getProperty("cvpeople:ifRivistaFonte") != null
									&& riga.getProperty("cvpeople:ifRivistaFonte")
											.getValues().size() != 0){
								for (Object ifRivistaFonte : riga.getProperty("cvpeople:ifRivistaFonte").getValues()) {
									if (!ifRivistaFonte.equals("Altro")) {
										if (detail.getIfFonte() != null) {
											detail.setIfFonte(String.valueOf(ifRivistaFonte).replace("_", " ") + "," + detail.getIfFonte());
										} else {
											detail.setIfFonte(String.valueOf(ifRivistaFonte).replace("_", " "));
										}
									}
								}
							}
							
							
							result.add(detail);
						}
					}
				}
			}
		}
		return result;
	}

	private Map<String, List<Pair<String, String>>> getSezioni(List<String> propertyValue, Session cmisSession) {
		Map<String, List<Pair<String, String>>> sezioni = new LinkedHashMap<String, List<Pair<String, String>>>();
		for (String type : propertyValue) {
			BulkInfo bulkInfo = bulkInfoService.find(type);
			String sezione = bulkInfo.getShortDescription();
			if (sezione == null || sezione.length() == 0)
				sezione = cmisSession.getTypeDefinition(type).getDisplayName();
			String sottoSezione = bulkInfo.getLongDescription();
			String queryName = bulkInfo.getCmisQueryName();
			if (sezioni.containsKey(sezione)) {
				sezioni.get(sezione).add(
						new Pair<String, String>(sottoSezione, queryName));
			} else {
				List<Pair<String, String>> lista = new ArrayList<Pair<String, String>>();
				lista.add(new Pair<String, String>(sottoSezione, queryName));
				sezioni.put(sezione, lista);
			}
		}
		return sezioni;
	}
	@SuppressWarnings("unchecked")
	private void printField(FieldPropertySet printForm, ApplicationModel applicationModel, Folder application, PrintDetailBulk detail, BulkInfo bulkInfo) {
		for (FieldProperty printFieldProperty : printForm
				.getFieldProperties()) {
			if (printFieldProperty.getAttribute("formName") != null) {
				Object objValue = application.getPropertyValue(printFieldProperty.getAttribute("formName"));
				FieldPropertySet printFormDetail = bulkInfo.getPrintForms().get(printFieldProperty.getAttribute("formName"));
				for (FieldProperty printFieldPropertyDetail : printFormDetail.getFieldProperties()) {
					if (printFieldPropertyDetail.getAttribute("key") != null && printFieldPropertyDetail.getAttribute("key").equals(String.valueOf(objValue))) {
						detail.addField(new Pair<String, String>(null, applicationModel.getMessage(printFieldPropertyDetail.getAttribute("label"))));
					}
				}
				continue;
			}			
			String message;
			String label = printFieldProperty
					.getAttribute("label");
			if (label == null) {
				JSONObject jsonLabel = new JSONObject(printFieldProperty
						.getAttribute("jsonlabel"));
				message = applicationModel.getMessage(jsonLabel.getString("key"));
				if (message == null || message.equalsIgnoreCase(jsonLabel.getString("key")))
					message = jsonLabel.getString("default");
			} else {			
				message = applicationModel.getMessage(label);
			}
			String value;
			Object objValue = application
					.getPropertyValue(printFieldProperty
							.getProperty());
			if (objValue == null && printFieldProperty
					.getProperty() != null)
				return;
			else if (printFieldProperty
					.getProperty() == null) {
				detail.addField(new Pair<String, String>(null, message));
			} else {
				if (application.getProperty(
						printFieldProperty.getProperty())
						.isMultiValued()) {
					List<Object> values = (List<Object>) objValue;
					if (values.isEmpty())
						return;
					if (values.size() > 1) {
						for (int k = 0; k < values.size(); k++) {
							detail.addField(new Pair<String, String>(k == 0 ? (message + "<br>"): "", String.valueOf(values.get(k))));
						}									
					} else {
						value = StringUtils.collectionToDelimitedString(((Collection<?>) objValue),", ");
						detail.addField(new Pair<String, String>(message, value));	
					}
				} else {
					if (printFieldProperty.getAttribute("widget") != null) {
						if (printFieldProperty.getAttribute("widget").contains("ui.datepicker")) {
							value = StringUtil.DATEFORMAT.format(((Calendar)objValue).getTime());
						} else if (printFieldProperty.getAttribute("widget").contains("ui.datetimepicker")) {
							value = StringUtil.DATETIMEFORMAT.format(((Calendar)objValue).getTime());
						} else {
							if (objValue instanceof Boolean) {
								if (printFieldProperty.getAttribute("generated") != null)
									value = Boolean.valueOf(String.valueOf(objValue))?"Si":"No";
								else
									value = "";
							} else {
								value = String.valueOf(objValue);
							}
						}
					} else {	
						value = String.valueOf(objValue);
					}	
					detail.addField(new Pair<String, String>(message, value));								
				}
			}
		}		
	}	

	@SuppressWarnings("unchecked")
	private List<Pair<String, String>> getFields(CmisObject riga,
			ApplicationModel applicationModel) {

		BulkInfo bulkInfo = bulkInfoService.find(riga.getType().getId());

		List<Pair<String, String>> results = new ArrayList<Pair<String, String>>();
		if (bulkInfo == null) {
			for (Property<?> property : riga.getProperties()) {
				if (!property.getDefinition().isInherited()
						&& !property.getDefinition().getId()
								.startsWith("cm:owner")) {
					results.add(new Pair<String, String>(property
							.getDisplayName(), property.getValueAsString()));
				}
			}
			results.add(new Pair<String, String>(riga.getProperty(
					PropertyIds.NAME).getDisplayName(), riga.getProperty(
					PropertyIds.NAME).getValueAsString()));
		} else {
			for (FieldProperty fieldProperty : bulkInfo.getForm("default")) {
				String value;
				Object objValue = riga.getPropertyValue(fieldProperty
						.getProperty());
				if (riga.getProperty(fieldProperty.getProperty()) != null) {
					PropertyDefinition<?> propertyDefinition = riga
							.getProperty(fieldProperty.getProperty())
							.getDefinition();
					Cardinality cardinality = propertyDefinition
							.getCardinality();
					if ((cardinality.equals(Cardinality.SINGLE) && objValue != null)
							|| (cardinality.equals(Cardinality.MULTI)
									&& objValue != null && !((List<Object>) objValue)
										.isEmpty())) { // ci sono due objValue
														// != null
						if (cardinality.equals(Cardinality.MULTI)) {
							value = StringUtils.collectionToDelimitedString(((Collection<?>) objValue),", ");
						} else {
							if (propertyDefinition instanceof PropertyDateTimeDefinition) {
								value = new SimpleDateFormat("dd/MM/yyyy",
										Locale.ITALY)
										.format(((GregorianCalendar) objValue)
												.getTime());
							} else if (propertyDefinition instanceof PropertyDecimalDefinition) {
								value = new NumberFormatter("").print(
										(BigDecimal) objValue, Locale.ITALY);
							} else if (propertyDefinition instanceof PropertyBooleanDefinition) {
								if (!Boolean.valueOf(objValue.toString()))
									continue;
								value = "";
							} else {
								value = objValue.toString();
							}
						}
						String message = displayValue(fieldProperty, value,
								applicationModel);
						results.add(new Pair<String, String>(applicationModel
								.getMessage(getLabel(fieldProperty,
										applicationModel)), message));
					}
				}
			}
		}
		return results;
	}

	private void addOrderCurriculum(Session cmisSession, String queryName,
			Criteria criteria) {
		ObjectType type = cmisSession.getTypeDefinition("D:"
				.concat(queryName));
        LOGGER.debug(type.getDisplayName());
		String aspectQueryName = null, aspectPropertyOrder = null;
		if (typeService.getMandatoryAspects(type).contains(
				JCONONPolicyType.CV_COMMON_METADATA_ASPECT2.value())) {
			aspectQueryName = JCONONPolicyType.CV_COMMON_METADATA_ASPECT2
					.queryName();
			aspectPropertyOrder = "common.cvelement:periodAttivitaDal";
		} else if (typeService.getMandatoryAspects(type).contains(
				JCONONPolicyType.CV_COMMON_PREMIO.value())) {
			aspectQueryName = JCONONPolicyType.CV_COMMON_PREMIO.queryName();
			aspectPropertyOrder = "common.cvelement:data";
		}
		if (aspectQueryName != null) {
			Criteria criteriaCommon = criteria.createCriteria(aspectQueryName,
					"common");
			criteriaCommon.addJoinCriterion(Restrictions.eqProperty(
					criteria.prefix(PropertyIds.OBJECT_ID),
					criteriaCommon.prefix(PropertyIds.OBJECT_ID)));
			criteriaCommon.addOrder(Order.desc(aspectPropertyOrder));
		}
		criteria.addOrder(Order.desc(PropertyIds.NAME));
	}

	private String displayValue(FieldProperty fieldProperty, String value,
			ApplicationModel applicationModel) {
		if (fieldProperty.getAttribute("jsonlist") != null) {
			String jsonString = fieldProperty.getAttribute("jsonlist");
			JsonElement item = new JsonParser().parse(jsonString);
			JsonArray json = item.getAsJsonArray();
			for (int i = 0; i < json.size(); i++) {
				JsonObject appo = json.get(i).getAsJsonObject();
				if (value.equals(appo.get("key").getAsString())) {
					String i18nLabel = appo.get("label").getAsString();
					if (i18nLabel
							.equals(applicationModel.getMessage(i18nLabel)))
						return appo.get("defaultLabel").getAsString();
					return applicationModel.getMessage(i18nLabel);
				}
			}
		}
		return value;
	}

	private String getLabel(FieldProperty fieldProperty,
			ApplicationModel applicationModel) {
		if (fieldProperty.getAttribute("label") != null)
			return fieldProperty.getAttribute("label");
		else if (fieldProperty.getSubProperty("jsonlabel") != null) {
			String key = fieldProperty.getSubProperty("jsonlabel").getAttribute("key");
			String defaultLabel = fieldProperty.getSubProperty("jsonlabel").getAttribute("default");
			if (applicationModel.getMessage(key).equals(key))
				return defaultLabel;
			else
				return key;
		} else
			return fieldProperty.getAttribute("name");
	}	
	
	public String getSchedaValutazioneName(Session cmisSession, Folder application) throws CMISApplicationException {
		String shortNameEnte = "CNR";
		Folder call = (Folder) cmisSession.getObject(application.getParentId());
		DateFormat formatter = new SimpleDateFormat("yyyy/MM/dd");
		GregorianCalendar dataDomanda = application.getPropertyValue(JCONONPropertyIds.APPLICATION_DATA_DOMANDA.value());
		String dataApplication = "PROVVISORIA";
		if (dataDomanda != null)
			dataApplication = formatter.format(dataDomanda.getTime()).replace("/", "_");
		return shortNameEnte +
				"-" +
				call.getPropertyValue(JCONONPropertyIds.CALL_CODICE.value())+
				"-RD-" +
				application.getPropertyValue(JCONONPropertyIds.APPLICATION_USER.value())+
				"-" +
				dataApplication +
				".xls";
	}

	public String getSchedaAnonimaSinteticaName(Session cmisSession, Folder application, int index) throws CMISApplicationException {
		String shortNameEnte = "CNR";
		Folder call = (Folder) cmisSession.getObject(application.getParentId());
		return shortNameEnte +
				"-" +
				call.getPropertyValue(JCONONPropertyIds.CALL_CODICE.value())+
				"-RD-" +
				String.format("%4s", index).replace(' ', '0') + 
				".pdf";
	}

	public byte[] getSchedaValutazione(Session cmisSession, Folder application,
			String contextURL, Locale locale) throws CMISApplicationException {
		Folder call = application.getFolderParent();
		ApplicationModel applicationModel = new ApplicationModel(application,
				cmisSession.getDefaultContext(),
				i18nService.loadLabels(locale), contextURL, false);

		final Gson gson = new GsonBuilder()
		.setDateFormat("yyyy-MM-dd'T'HH:mm:ss")
		.excludeFieldsWithoutExposeAnnotation()
		.registerTypeAdapter(GregorianCalendar.class, new JsonSerializer<GregorianCalendar>() {
			@Override
			public JsonElement serialize(GregorianCalendar src, Type typeOfSrc,
					JsonSerializationContext context) {
				return  context.serialize(src.getTime());
			}
		}).create();
		if (call.getPropertyValue(JCONONPropertyIds.CALL_ELENCO_ASSOCIATIONS.value()) != null) {
			applicationModel
					.getProperties()
					.put("allegati",
							getAllegati(
									application,
									JCONONPolicyType.JCONON_ATTACHMENT_GENERIC_DOCUMENT,
									cmisSession, applicationModel, false));
		}

		if (call.getPropertyValue(JCONONPropertyIds.CALL_ELENCO_SEZIONE_CURRICULUM.value()) != null) {
			applicationModel
					.getProperties()
					.put("curriculum",
							getCurriculum(
					(List<String>)call.getPropertyValue(JCONONPropertyIds.CALL_ELENCO_SEZIONE_CURRICULUM.value()),
											application,
											cmisSession, applicationModel, false));
		}
		if (call.getPropertyValue(JCONONPropertyIds.CALL_ELENCO_SEZIONE_PRODOTTI.value()) != null) {
			applicationModel
					.getProperties()
					.put("prodotti",
							getProdotti(
					(List<String>)call.getPropertyValue(JCONONPropertyIds.CALL_ELENCO_SEZIONE_PRODOTTI.value()),
											application,
											JCONONPolicyType.PEOPLE_NO_SELECTED_PRODUCT,
											cmisSession, applicationModel, false));
			applicationModel
					.getProperties()
					.put("prodottiScelti",
							getProdotti(
					(List<String>)call.getPropertyValue(JCONONPropertyIds.CALL_ELENCO_SEZIONE_PRODOTTI.value()),
											application,
											JCONONPolicyType.PEOPLE_SELECTED_PRODUCT,
											cmisSession, applicationModel, false));
		}

		String json = "{\"properties\":"+gson.toJson(applicationModel.getProperties())+"}";
		LOGGER.debug(json);
		try {

			Map<String, Object> parameters = new HashMap<String, Object>();
			JRDataSource datasource = new JsonDataSource(new ByteArrayInputStream(json.getBytes(Charset.forName("UTF-8"))), "properties");
			final ResourceBundle resourceBundle = ResourceBundle.getBundle(
					"net.sf.jasperreports.view.viewer", locale);
			parameters.put(JRParameter.REPORT_LOCALE, locale);
			parameters.put(JRParameter.REPORT_RESOURCE_BUNDLE, resourceBundle);
			parameters.put(JRParameter.REPORT_DATA_SOURCE, datasource);
			parameters.put("DIR_IMAGE", this.getClass().getResource("/it/cnr/jconon/print/").getPath());
			parameters.put("SUBREPORT_DIR", this.getClass().getResource("/it/cnr/jconon/print/").getPath());

			ClassLoader classLoader = ClassLoader.getSystemClassLoader();
			parameters.put(JRParameter.REPORT_CLASS_LOADER, classLoader);

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			JasperReport report = JasperCompileManager.compileReport(this.getClass().getResourceAsStream("/it/cnr/jconon/print/scheda_valutazione.jrxml"));		
			JasperPrint jasperPrint = JasperFillManager.fillReport(report, parameters);
			
			JRXlsExporter exporter = new JRXlsExporter();
			exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
			exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(baos));
			SimpleXlsReportConfiguration configuration = new SimpleXlsReportConfiguration();
			configuration.setOnePagePerSheet(false);
			configuration.setDetectCellType(true);
			configuration.setCollapseRowSpan(false);
			configuration.setRemoveEmptySpaceBetweenRows(true);
			configuration.setWhitePageBackground(false);
			exporter.setConfiguration(configuration);			
			exporter.exportReport();
			return baos.toByteArray();
		} catch (Exception e) {
			throw new CMISApplicationException("Error in JASPER", e);
		}
	}
	
	@SuppressWarnings("unchecked")
	public byte[] getSchedaAnonimaSintetica(Session cmisSession, Folder application,
			String contextURL, Locale locale, int index) throws CMISApplicationException {
		Folder call = application.getFolderParent();
		ApplicationModel applicationModel = new ApplicationModel(application,
				cmisSession.getDefaultContext(),
				i18nService.loadLabels(locale), contextURL, false);		
		
		final Gson gson = new GsonBuilder()
		.setDateFormat("yyyy-MM-dd'T'HH:mm:ss")
		.excludeFieldsWithoutExposeAnnotation()
		.registerTypeAdapter(GregorianCalendar.class, new JsonSerializer<GregorianCalendar>() {
			@Override
			public JsonElement serialize(GregorianCalendar src, Type typeOfSrc,
					JsonSerializationContext context) {
				return  context.serialize(src.getTime());
			}
		}).create();
		if (call.getPropertyValue(JCONONPropertyIds.CALL_ELENCO_SEZIONE_SCHEDE_ANONIME.value()) != null) {
			applicationModel.getProperties().put("schedeAnonime", getCurriculum(
					(List<String>) call
					.getPropertyValue(JCONONPropertyIds.CALL_ELENCO_SEZIONE_SCHEDE_ANONIME
							.value()),
							application, cmisSession, applicationModel));
		}				
		String json = "{\"properties\":"+gson.toJson(applicationModel.getProperties())+"}";
		try {
			Map<String, Object> parameters = new HashMap<String, Object>();
			JRDataSource datasource = new JsonDataSource(new ByteArrayInputStream(json.getBytes(Charset.forName("UTF-8"))), "properties");
			JRGzipVirtualizer vir = new JRGzipVirtualizer(100);
			final ResourceBundle resourceBundle = ResourceBundle.getBundle(
					"net.sf.jasperreports.view.viewer", locale);
			parameters.put(JRParameter.REPORT_LOCALE, locale);
			parameters.put(JRParameter.REPORT_RESOURCE_BUNDLE, resourceBundle);
			parameters.put(JRParameter.REPORT_DATA_SOURCE, datasource);
			parameters.put(JRParameter.REPORT_VIRTUALIZER, vir);
			parameters.put("INDICE", index);
			parameters.put("DIR_IMAGE", this.getClass().getResource("/it/cnr/jconon/print/").getPath());
			parameters.put("SUBREPORT_DIR", this.getClass().getResource("/it/cnr/jconon/print/").getPath());

			ClassLoader classLoader = ClassLoader.getSystemClassLoader();
			parameters.put(JRParameter.REPORT_CLASS_LOADER, classLoader);

			JasperPrint jasperPrint = JasperFillManager.fillReport(this.getClass().getResourceAsStream("/it/cnr/jconon/print/SchedaAnonima.jasper"), parameters);


			ByteArrayOutputStream os = new ByteArrayOutputStream();
			JRPdfExporter exporter = new JRPdfExporter();
			exporter.setParameter(JRExporterParameter.JASPER_PRINT, jasperPrint);
			exporter.setParameter(JRExporterParameter.OUTPUT_STREAM, os);
			exporter.setParameter(JRPdfExporterParameter.FORCE_LINEBREAK_POLICY, Boolean.TRUE);
			exporter.exportReport();
			return os.toByteArray();
		} catch (Exception e) {
			throw new CMISApplicationException("Error in JASPER", e);
		}
	}	
	@SuppressWarnings("deprecation")
	public void addContentToCmisObject(ApplicationModel applicationBulk,
			CmisObject cmisObject, Locale locale) {
		BulkInfo bulkInfo = bulkInfoService.find(cmisObject.getType().getId());
		String title = bulkInfo.getLongDescription();
		if (title == null || title.length() == 0)
			title = cmisObject.getType().getDisplayName();
		
		Map<String, Object> parameters = new HashMap<String, Object>();
		final Gson gson = new GsonBuilder()
		.setDateFormat("yyyy-MM-dd'T'HH:mm:ss")
		.excludeFieldsWithoutExposeAnnotation()
		.registerTypeAdapter(GregorianCalendar.class, new JsonSerializer<GregorianCalendar>() {
			@Override
			public JsonElement serialize(GregorianCalendar src, Type typeOfSrc,
					JsonSerializationContext context) {
				return  context.serialize(src.getTime());
			}
		}).create();
		List<Pair<String, String>> fields = new ArrayList<Pair<String, String>>();
		fields.addAll(getFields(cmisObject, applicationBulk));
		applicationBulk.getProperties().put("fields", new PrintDetailBulk(null, null, null, fields, null));
		String json = "{\"properties\":"+gson.toJson(applicationBulk.getProperties())+"}";

		JRGzipVirtualizer vir = new JRGzipVirtualizer(100);
		final ResourceBundle resourceBundle = ResourceBundle.getBundle(
				"net.sf.jasperreports.view.viewer", locale);
		try {
			JRDataSource datasource = new JsonDataSource(new ByteArrayInputStream(json.getBytes(Charset.forName("UTF-8"))), "properties");
			parameters.put(JRParameter.REPORT_LOCALE, locale);
			parameters.put(JRParameter.REPORT_RESOURCE_BUNDLE, resourceBundle);
			parameters.put(JRParameter.REPORT_DATA_SOURCE, datasource);
			parameters.put(JRParameter.REPORT_VIRTUALIZER, vir);
			parameters.put("DIR_IMAGE", this.getClass().getResource("/it/cnr/jconon/print/").getPath());
			parameters.put("SUBREPORT_DIR", this.getClass().getResource("/it/cnr/jconon/print/").getPath());			
			parameters.put("title", title);
			ClassLoader classLoader = ClassLoader.getSystemClassLoader();
			parameters.put(JRParameter.REPORT_CLASS_LOADER, classLoader);

			JasperPrint jasperPrint = JasperFillManager.fillReport(this.getClass().getResourceAsStream("/it/cnr/jconon/print/prodotti.jasper"), parameters);
			InputStream stream = new ByteArrayInputStream(JasperExportManager.exportReportToPdf(jasperPrint));
			ContentStream contentStream = new ContentStreamImpl(cmisObject.getName(), new BigInteger(String.valueOf(stream.available())), "application/pdf", stream);
			((Document)cmisObject).setContentStream(contentStream, true);
		} catch (Exception e) {
			LOGGER.error("Error during print report for object: " + cmisObject.getId(), e);
		}
	}
	
	public byte[] printDichiarazioneSostitutiva(Session cmisSession, String nodeRef, String contextURL, Locale locale) throws CMISApplicationException {
		return getDichiarazioneSostitutiva(cmisSession, (Folder)cmisSession.getObject(nodeRef), contextURL, locale);
	}
	
	@SuppressWarnings("deprecation")
	public byte[] getDichiarazioneSostitutiva(Session cmisSession, Folder application, String contextURL, Locale locale) throws CMISApplicationException {

		ApplicationModel applicationBulk = new ApplicationModel(application,
				cmisSession.getDefaultContext(),
				i18nService.loadLabels(locale), contextURL);

		final Gson gson = new GsonBuilder()
		.setDateFormat("yyyy-MM-dd'T'HH:mm:ss")
		.excludeFieldsWithoutExposeAnnotation()
		.registerTypeAdapter(GregorianCalendar.class, new JsonSerializer<GregorianCalendar>() {
			@Override
			public JsonElement serialize(GregorianCalendar src, Type typeOfSrc,
					JsonSerializationContext context) {
				return  context.serialize(src.getTime());
			}
		}).create();
		String json = "{\"properties\":"+gson.toJson(applicationBulk.getProperties())+"}";
		LOGGER.debug(json);
		try {

			Map<String, Object> parameters = new HashMap<String, Object>();
			JRDataSource datasource = new JsonDataSource(new ByteArrayInputStream(json.getBytes(Charset.forName("UTF-8"))), "properties");
			JRGzipVirtualizer vir = new JRGzipVirtualizer(100);
			final ResourceBundle resourceBundle = ResourceBundle.getBundle(
					"net.sf.jasperreports.view.viewer", locale);
			parameters.put(JRParameter.REPORT_LOCALE, locale);
			parameters.put(JRParameter.REPORT_RESOURCE_BUNDLE, resourceBundle);
			parameters.put(JRParameter.REPORT_DATA_SOURCE, datasource);
			parameters.put(JRParameter.REPORT_VIRTUALIZER, vir);
			parameters.put("DIR_IMAGE", this.getClass().getResource("/it/cnr/jconon/print/").getPath());
			parameters.put("SUBREPORT_DIR", this.getClass().getResource("/it/cnr/jconon/print/").getPath());

			ClassLoader classLoader = ClassLoader.getSystemClassLoader();
			parameters.put(JRParameter.REPORT_CLASS_LOADER, classLoader);

			JasperPrint jasperPrint = JasperFillManager.fillReport(this.getClass().getResourceAsStream("/it/cnr/jconon/print/DichiarazioneSostitutiva.jasper"), parameters);
			return JasperExportManager.exportReportToPdf(jasperPrint);
		} catch (Exception e) {
			throw new CMISApplicationException("Error in JASPER", e);
		}
	}	
}
