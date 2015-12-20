package com.tikal.fleettracker.notifications;

import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.Set;

import com.cyngn.kafka.MessageConsumer;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mail.LoginOption;
import io.vertx.ext.mail.MailClient;
import io.vertx.ext.mail.MailConfig;
import io.vertx.ext.mail.MailMessage;
import io.vertx.ext.mail.MailResult;
import io.vertx.ext.mail.StartTLSOptions;

public class LongTransitEmailNotificationVerticle extends AbstractVerticle {

	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(LongTransitEmailNotificationVerticle.class);
	private MailClient mailClient ;
	private final SimpleDateFormat df = new SimpleDateFormat("yyMMddHHmmss");
	private HttpClient managementHttpClient;
	
	private boolean blockEmailsSending;
	
	private int longTransitThresholdInSec;
	
	private final Set<String> longTransits = new HashSet<>();

	@Override
	public void start() {
		longTransitThresholdInSec = config().getInteger("longTransitThresholdInSec");
		vertx.deployVerticle(MessageConsumer.class.getName(),new DeploymentOptions().setConfig(config()),this::handleKafkaDeploy);
		
		final MailConfig mailConfig = new MailConfig().setHostname(config().getString("mail.host")).setPort(config().getInteger("mail.port")).setSsl(true)
				.setStarttls(StartTLSOptions.REQUIRED).setLogin(LoginOption.REQUIRED).setAuthMethods("PLAIN")
				.setUsername(config().getString("mail.username")).setPassword(config().getString("mail.password"));

		mailClient = MailClient.createShared(vertx, mailConfig);
		
		managementHttpClient = vertx.createHttpClient(
				new HttpClientOptions().setDefaultHost(config().getString("management.http.server.address"))
						.setDefaultPort(config().getInteger("management.http.server.port")));
		
		blockEmailsSending = config().getBoolean("blockEmailsSending");
		
		if(blockEmailsSending)
			logger.warn("***** WILL NOT SEND MAILS AS IT IS CONFIGURED TO BE BLOCKED!!! *****");
		
		logger.info("Started the HTTP server...");

	}

	private void handleEmailNotification(final Message<String> m) {	
		final JsonObject segment = new JsonObject(m.body());
		logger.debug("Got segment message {}",segment);
		final String segmentId = segment.getString("_id");
		if((!longTransits.contains(segmentId)) && segment.getString("segmentType").equals("transit") && segment.getInteger("duration") > longTransitThresholdInSec){
			longTransits.add(segmentId);
			final Integer vehicleId = segment.getInteger("vehicleId");
			managementHttpClient.get(
				"/api/v1/vehicles/"+vehicleId+"/guardian/email", 
				response->handleResponse(response,vehicleId, segment)).putHeader("content-type", "text/json").end();
		}
		
		
	}

	private void handleResponse(final HttpClientResponse response, final Integer vehicleId,final JsonObject segment) {
		if(response.statusCode() != 200){
			logger.error("Could not find email for guardian's vehicle {}: {}",vehicleId,response.statusMessage());
		}else{			
			response.bodyHandler(body -> {			
				if(body==null || body.toString().isEmpty()){
					logger.error("Could not find email for guardian's vehicle {}",vehicleId);
				}
				else{
					sendMail(vehicleId, segment, body.toString());
				}
			});
		}
	}

	private void sendMail(final Integer vehicleId,final JsonObject segment,final String guardianEmail) {
		try {
			final String subject = String.format("A long transit for vehcile %s",vehicleId);
			final String body = String.format("The vehicle %s is in transit for more time than allowed. It is in transit since %s",vehicleId,df.parse(String.valueOf(segment.getLong("startTime"))));
			final MailMessage email = new MailMessage().setFrom("fleettrackerdemo@gmail.com").setTo(guardianEmail)
				.setSubject(subject)
				.setHtml(body);
			if(blockEmailsSending)
				logger.trace("Did not send mail to {} , as we are mails are configured to be blocked. segment is {}",guardianEmail,segment);
			else
				mailClient.sendMail(email, result -> handleMailSent(result));
		} catch (final Exception e) {
			logger.error("Failed to send mail ",e);
		}
	}

	private void handleMailSent(final AsyncResult<MailResult> result) {
		if(result.succeeded()){
			logger.debug(result.result().toString());
		}else{
			logger.error("Mail sent failed ",result.cause());
		}
			
	}
	
	private void handleKafkaDeploy(final AsyncResult<String> ar) {
		if (ar.succeeded()){
			logger.info("Connected to succfully to Kafka");
			vertx.eventBus().consumer(MessageConsumer.EVENTBUS_DEFAULT_ADDRESS, this::handleEmailNotification);
		}
		else
			logger.error("Problem connect to Kafka: ",ar.cause());
	}

}
