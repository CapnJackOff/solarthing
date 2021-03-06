package me.retrodaredevil.solarthing.program.pvoutput;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import me.retrodaredevil.couchdb.CouchProperties;
import me.retrodaredevil.couchdb.EktorpUtil;
import me.retrodaredevil.solarthing.SolarThingConstants;
import me.retrodaredevil.solarthing.analytics.AnalyticsManager;
import me.retrodaredevil.solarthing.config.databases.DatabaseType;
import me.retrodaredevil.solarthing.config.databases.implementations.CouchDbDatabaseSettings;
import me.retrodaredevil.solarthing.config.options.PVOutputUploadProgramOptions;
import me.retrodaredevil.solarthing.config.options.ProgramType;
import me.retrodaredevil.solarthing.couchdb.CouchDbQueryHandler;
import me.retrodaredevil.solarthing.couchdb.SolarThingCouchDb;
import me.retrodaredevil.solarthing.misc.device.DevicePacket;
import me.retrodaredevil.solarthing.misc.error.ErrorPacket;
import me.retrodaredevil.solarthing.packets.collection.DefaultInstanceOptions;
import me.retrodaredevil.solarthing.packets.collection.FragmentedPacketGroup;
import me.retrodaredevil.solarthing.packets.collection.PacketGroups;
import me.retrodaredevil.solarthing.packets.collection.parsing.*;
import me.retrodaredevil.solarthing.packets.handling.PacketHandleException;
import me.retrodaredevil.solarthing.packets.instance.InstancePacket;
import me.retrodaredevil.solarthing.program.DatabaseConfig;
import me.retrodaredevil.solarthing.program.PacketUtil;
import me.retrodaredevil.solarthing.program.SolarMain;
import me.retrodaredevil.solarthing.pvoutput.CsvUtil;
import me.retrodaredevil.solarthing.pvoutput.SimpleDate;
import me.retrodaredevil.solarthing.pvoutput.data.*;
import me.retrodaredevil.solarthing.pvoutput.service.OkHttpUtil;
import me.retrodaredevil.solarthing.pvoutput.service.PVOutputService;
import me.retrodaredevil.solarthing.pvoutput.service.RetrofitUtil;
import me.retrodaredevil.solarthing.solar.SolarStatusPacket;
import me.retrodaredevil.solarthing.solar.extra.SolarExtraPacket;
import me.retrodaredevil.solarthing.util.JacksonUtil;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.ektorp.CouchDbInstance;
import org.ektorp.ViewQuery;
import org.ektorp.http.HttpClient;
import org.ektorp.impl.StdCouchDbConnector;
import org.ektorp.impl.StdCouchDbInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class PVOutputUploadMain {
	private static final Logger LOGGER = LoggerFactory.getLogger(PVOutputUploadMain.class);
	private static final ObjectMapper MAPPER = JacksonUtil.lenientMapper(JacksonUtil.defaultMapper());
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");


	@SuppressWarnings("SameReturnValue")
	public static int startPVOutputUpload(PVOutputUploadProgramOptions options, String[] extraArgs, File dataDirectory){
		LOGGER.info(SolarThingConstants.SUMMARY_MARKER, "Starting PV Output upload program");
		TimeZone timeZone = options.getTimeZone();
		LOGGER.info(SolarThingConstants.SUMMARY_MARKER, "Using time zone: {}", timeZone.getDisplayName());
		LOGGER.info("Using default instance options: " + options.getDefaultInstanceOptions());
		DatabaseConfig databaseConfig = SolarMain.getDatabaseConfig(options.getDatabase());
		DatabaseType databaseType = databaseConfig.getType();
		if(databaseType != CouchDbDatabaseSettings.TYPE){
			LOGGER.error(SolarThingConstants.SUMMARY_MARKER, "(Fatal)Only CouchDb can be used for this program type right now!");
			return 1;
		}
		CouchDbDatabaseSettings couchDbDatabaseSettings = (CouchDbDatabaseSettings) databaseConfig.getSettings();
		CouchProperties couchProperties = couchDbDatabaseSettings.getCouchProperties();
		final CouchDbQueryHandler queryHandler;
		{
			final HttpClient httpClient = EktorpUtil.createHttpClient(couchProperties);
			CouchDbInstance instance = new StdCouchDbInstance(httpClient);
			queryHandler = new CouchDbQueryHandler(new StdCouchDbConnector(SolarThingConstants.SOLAR_STATUS_UNIQUE_NAME, instance), false);
		}
		PacketGroupParser statusParser = new SimplePacketGroupParser(new PacketParserMultiplexer(Arrays.asList(
				new ObjectMapperPacketConverter(MAPPER, SolarStatusPacket.class),
				new ObjectMapperPacketConverter(MAPPER, SolarExtraPacket.class),
				new ObjectMapperPacketConverter(MAPPER, DevicePacket.class),
				new ObjectMapperPacketConverter(MAPPER, ErrorPacket.class),
				new ObjectMapperPacketConverter(MAPPER, InstancePacket.class)
		), PacketParserMultiplexer.LenientType.FAIL_WHEN_UNHANDLED_WITH_EXCEPTION));

		OkHttpClient client = OkHttpUtil.configure(new OkHttpClient.Builder(), options.getApiKey(), options.getSystemId())
				.addInterceptor(new HttpLoggingInterceptor(LOGGER::debug).setLevel(HttpLoggingInterceptor.Level.BASIC))
				.build();
		Retrofit retrofit = RetrofitUtil.defaultBuilder().client(client).build();
		PVOutputService service = retrofit.create(PVOutputService.class);
		PVOutputHandler handler = new PVOutputHandler(timeZone, options.getRequiredIdentifierMap());
		if(extraArgs.length >= 2) {
			System.out.println("Starting range upload");
			String fromDateString = extraArgs[0];
			String toDateString = extraArgs[1];
			final SimpleDate fromDate;
			final SimpleDate toDate;
			try {
				fromDate = SimpleDate.fromDate(DATE_FORMAT.parse(fromDateString));
				toDate = SimpleDate.fromDate(DATE_FORMAT.parse(toDateString));
			} catch (ParseException e) {
				e.printStackTrace();
				System.err.println("Unable to parser either from date or to date. Use the yyyy-MM-dd format");
				return 1;
			}
			return startRangeUpload(
					fromDate, toDate,
					options, queryHandler, statusParser, handler, service, options.getTimeZone()
			);
		} else if (extraArgs.length == 1) {
			LOGGER.error(SolarThingConstants.SUMMARY_MARKER, "(Fatal)You need 2 arguments for range upload! You have 1!");
			return 1;
		}
		AnalyticsManager analyticsManager = new AnalyticsManager(options.isAnalyticsEnabled(), dataDirectory);
		analyticsManager.sendStartUp(ProgramType.PVOUTPUT_UPLOAD);

		return startRealTimeProgram(options, queryHandler, statusParser, handler, service, options.getTimeZone());
	}
	private static int startRangeUpload(
			SimpleDate fromDate, SimpleDate toDate,
			PVOutputUploadProgramOptions options, CouchDbQueryHandler queryHandler,
			PacketGroupParser statusParser, PVOutputHandler handler, PVOutputService service, TimeZone timeZone
	) {
		List<AddOutputParameters> addOutputParameters = new ArrayList<>();
		SimpleDate date = fromDate;
		while(date.compareTo(toDate) <= 0) {
			System.out.println("Doing " + date);
			SimpleDate tomorrow = date.tomorrow();
			long dayStart = date.getDayStartDateMillis(timeZone);
			long dayEnd = tomorrow.getDayStartDateMillis(timeZone);

			List<ObjectNode> statusPacketNodes = null;
			try {
				statusPacketNodes = queryHandler.query(SolarThingCouchDb.createMillisView()
						.startKey(dayStart)
						.endKey(dayEnd).inclusiveEnd(false));
				System.out.println("Got " + statusPacketNodes.size() + " packets for date: " + date.toPVOutputString());
			} catch (PacketHandleException e) {
				e.printStackTrace();
				System.err.println("Couldn't query packets. Skipping " + date.toPVOutputString());
			}
			if (statusPacketNodes != null) {
				List<FragmentedPacketGroup> packetGroups = PacketUtil.getPacketGroups(options.getSourceId(), options.getDefaultInstanceOptions(), statusPacketNodes, statusParser);

				if (packetGroups != null) {
					if (!handler.checkPackets(dayStart, packetGroups)) {
						System.err.println("Unsuccessfully checked packets for " + date.toPVOutputString());
						try {
							System.out.println(MAPPER.writeValueAsString(packetGroups.get(packetGroups.size() - 1)));
						} catch (JsonProcessingException e) {
							e.printStackTrace();
						}
					} else {
						AddStatusParameters statusParameters = handler.getStatus(dayStart, packetGroups);
						AddOutputParameters outputParameters = new AddOutputParametersBuilder(statusParameters.getDate())
								.setGenerated(statusParameters.getEnergyGeneration())
								.setConsumption(statusParameters.getEnergyConsumption())
								.build();
						addOutputParameters.add(outputParameters);
						System.out.println("Added parameters for " + date.toPVOutputString() + " to queue.");
						System.out.println("Generated: " + statusParameters.getEnergyGeneration());
						System.out.println(Arrays.toString(outputParameters.toBatchCsvArray()));
						System.out.println(CsvUtil.toCsvString(outputParameters.toBatchCsvArray()));
					}
				} else {
					System.err.println("Didn't find any packets with source: " + options.getSourceId() + " for date: " + date.toPVOutputString());
				}
			}

			date = tomorrow;
		}
		System.out.println("Going to upload in batches of 30...");
		for (int i = 0; i < addOutputParameters.size(); i += 30) {
			if (i != 0) {
				System.out.println("Sleeping...");
				try {
					//noinspection BusyWait
					Thread.sleep(7000);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					System.err.println("Interrupted");
					return 1;
				}
			}
			int endIndex = Math.min(i + 30, addOutputParameters.size());
			List<AddOutputParameters> parameters = addOutputParameters.subList(i, endIndex);
			System.out.println("Going to upload from " + parameters.get(0).getOutputDate().toPVOutputString() + " to " + parameters.get(parameters.size() - 1).getOutputDate().toPVOutputString());
			AddBatchOutputParameters batchOutputParameters = new ImmutableAddBatchOutputParameters(parameters);
			try {
				LOGGER.debug("Batch Output parameters as JSON: " + MAPPER.writeValueAsString(batchOutputParameters));
			} catch (JsonProcessingException e) {
				LOGGER.error("Got error serializing JSON. This should never happen.", e);
			}
			boolean successful = false;
			for (int j = 0; j < 5; j++) {
				if (j != 0) {
					System.out.println("Sleeping before trying again");
					try {
						//noinspection BusyWait
						Thread.sleep(j * 7000);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						System.err.println("Interrupted");
						return 1;
					}
				}
				Call<String> call = service.addBatchOutput(batchOutputParameters);
				final Response<String> response;
				try {
					response = call.execute();
				} catch (IOException e) {
					e.printStackTrace();
					System.err.println("Error while executing");
					continue;
				}
				if (response.isSuccessful()) {
					System.out.println("Executed successfully. Result: " + response.body());
					successful = true;
					break;
				} else {
					System.err.println("Unsuccessful. Message: " + response.message() + " code: " + response.code());
				}
			}
			if (!successful) {
				System.err.println("All tries were unsuccessful. Ending");
				return 1;
			}
		}
		System.out.println("Done!");
		return 0;
	}

	private static int startRealTimeProgram(
			PVOutputUploadProgramOptions options, CouchDbQueryHandler queryHandler,
			PacketGroupParser statusParser, PVOutputHandler handler, PVOutputService service, TimeZone timeZone
	) {
		while(!Thread.currentThread().isInterrupted()){
			LOGGER.debug("Going to do stuff now.");
			long now = System.currentTimeMillis();
			SimpleDate today = SimpleDate.fromDateMillis(now, timeZone);
			long dayStartTimeMillis = today.getDayStartDateMillis(timeZone);
			List<ObjectNode> statusPacketNodes = null;
			try {
				statusPacketNodes = queryHandler.query(SolarThingCouchDb.createMillisView()
						.startKey(dayStartTimeMillis)
						.endKey(now));
				LOGGER.debug("Got packets");
			} catch (PacketHandleException e) {
				LOGGER.error("Couldn't get status packets", e);
			}
			if(statusPacketNodes != null){
				List<FragmentedPacketGroup> packetGroups = PacketUtil.getPacketGroups(options.getSourceId(), options.getDefaultInstanceOptions(), statusPacketNodes, statusParser);
				if (packetGroups != null) {
					FragmentedPacketGroup latestPacketGroup = packetGroups.get(packetGroups.size() - 1);
					if (latestPacketGroup.getDateMillis() < System.currentTimeMillis() - 5 * 60 * 1000) {
						LOGGER.warn("The last packet is more than 5 minutes in the past!");
					} else if (!handler.checkPackets(dayStartTimeMillis, packetGroups)){
						LOGGER.warn("Checking packets unsuccessful.");
					} else {
						AddStatusParameters parameters = handler.getStatus(dayStartTimeMillis, packetGroups);
						uploadStatus(service, parameters);
					}
				} else {
					LOGGER.warn("Got " + statusPacketNodes.size() + " packets but, there must not have been any packets with the source: " + options.getSourceId());
				}
			}
			LOGGER.debug("Going to sleep now");
			try {
				//noinspection BusyWait
				Thread.sleep(5 * 60 * 1000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		return 1;
	}
	private static void uploadStatus(PVOutputService service, AddStatusParameters addStatusParameters) {
		try {
			LOGGER.debug("Status parameters as JSON: " + MAPPER.writeValueAsString(addStatusParameters));
		} catch (JsonProcessingException e) {
			LOGGER.error("Got error serializing JSON. This should never happen.", e);
		}
		Call<String> call = service.addStatus(addStatusParameters);
		executeCall(call);
	}
	private static void executeCall(Call<String> call) {
		LOGGER.debug("Executing call");
		Response<String> response = null;
		try {
			response = call.execute();
		} catch (IOException e) {
			LOGGER.error("Exception while executing", e);
		}
		if (response != null) {
			if (response.isSuccessful()) {
				LOGGER.debug("Executed successfully. Result: " + response.body());
			} else {
				LOGGER.debug("Unsuccessful. Message: " + response.message() + " code: " + response.code());
			}
		}
	}
}
