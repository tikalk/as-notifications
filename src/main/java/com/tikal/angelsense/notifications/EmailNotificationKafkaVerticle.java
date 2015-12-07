package com.tikal.angelsense.notifications;

import java.text.SimpleDateFormat;

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

public class EmailNotificationKafkaVerticle extends AbstractVerticle {

	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(EmailNotificationKafkaVerticle.class);
	private MailClient mailClient ;
	private final SimpleDateFormat df = new SimpleDateFormat("yyMMddHHmmss");
	private HttpClient managementHttpClient;
	
	private boolean blockEmailsSending;

	@Override
	public void start() {
		
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
		//We send mail only on closing place segment
		if(!segment.getBoolean("isOpen") && segment.getString("segmentType").equals("place")){
			final Integer angelId = segment.getInteger("angelId");
			managementHttpClient.get(
				"/api/v1/angels/"+angelId+"/guardian/email", 
				response->handleResponse(response,angelId, segment)).putHeader("content-type", "text/json").end();
		}
	}

	private void handleResponse(final HttpClientResponse response, final Integer angelId,final JsonObject segment) {
		if(response.statusCode() != 200){
			logger.error("Could not find email for guardian's angel {}: {}",angelId,response.statusMessage());
		}else{			
			response.bodyHandler(body -> {			
				if(body==null || body.toString().isEmpty()){
					logger.error("Could not find email for guardian's angel {}",angelId);
				}
				else{
					sendMail(angelId, segment, body.toString());
				}
			});
		}
	}

	private void sendMail(final Integer angelId,final JsonObject segment,final String guardianEmail) {
		try {
			final String body = String.format("The angel %s exited the place at %s", angelId,df.parse(String.valueOf(segment.getLong("startTime"))));
			final MailMessage email = new MailMessage().setFrom("angelsensedemo@gmail.com").setTo(guardianEmail)
				.setSubject("Exit segment")
				.setHtml(body);
			if(blockEmailsSending)
				logger.trace("Did not send mail to {} , as we are mails are configured to be blocked",guardianEmail);
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
