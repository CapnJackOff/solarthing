package me.retrodaredevil.solarthing.program;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.retrodaredevil.solarthing.SolarThingConstants;
import me.retrodaredevil.solarthing.analytics.AnalyticsManager;
import me.retrodaredevil.solarthing.analytics.RoverAnalyticsHandler;
import me.retrodaredevil.solarthing.config.options.ExtraOptionFlag;
import me.retrodaredevil.solarthing.config.options.ProgramType;
import me.retrodaredevil.solarthing.config.options.RequestProgramOptions;
import me.retrodaredevil.solarthing.config.request.DataRequester;
import me.retrodaredevil.solarthing.packets.Packet;
import me.retrodaredevil.solarthing.packets.collection.PacketCollectionIdGenerator;
import me.retrodaredevil.solarthing.packets.handling.PacketHandler;
import me.retrodaredevil.solarthing.packets.handling.PacketHandlerMultiplexer;
import me.retrodaredevil.solarthing.packets.handling.PacketListReceiver;
import me.retrodaredevil.solarthing.packets.handling.PacketListReceiverMultiplexer;
import me.retrodaredevil.solarthing.util.JacksonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RequestMain {
	private static final Logger LOGGER = LoggerFactory.getLogger(RequestMain.class);
	private static final ObjectMapper MAPPER = JacksonUtil.defaultMapper();

	public static int startRequestProgram(RequestProgramOptions options, File dataDirectory) {
		LOGGER.info(SolarThingConstants.SUMMARY_MARKER, "Beginning request program");
		PacketHandlerBundle packetHandlerBundle = PacketHandlerInit.getPacketHandlerBundle(SolarMain.getDatabaseConfigs(options), SolarThingConstants.SOLAR_STATUS_UNIQUE_NAME, SolarThingConstants.SOLAR_EVENT_UNIQUE_NAME);
		AnalyticsManager analyticsManager = new AnalyticsManager(options.isAnalyticsEnabled(), dataDirectory);
		analyticsManager.sendStartUp(ProgramType.REQUEST);
		List<PacketHandler> statusPacketHandlers = new ArrayList<>(packetHandlerBundle.getStatusPacketHandlers());
		statusPacketHandlers.add(new RoverAnalyticsHandler(analyticsManager));

//		List<PacketHandler> eventPacketHandlers = new ArrayList<>(packetHandlerBundle.getEventPacketHandlers());
		PacketHandler eventPacketHandler = new PacketHandlerMultiplexer(packetHandlerBundle.getEventPacketHandlers());

		PacketListReceiver sourceAndFragmentUpdater = SolarMain.getSourceAndFragmentUpdater(options);
		PacketListReceiverHandler eventPacketListReceiverHandler = new PacketListReceiverHandler(
				new PacketListReceiverMultiplexer(
						sourceAndFragmentUpdater,
						(packets, wasInstant) -> {
							LOGGER.debug(SolarThingConstants.NO_CONSOLE, "Debugging event packets");
							try {
								LOGGER.debug(SolarThingConstants.NO_CONSOLE, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(packets));
							} catch (JsonProcessingException e) {
								LOGGER.debug("Never mind about that...", e);
							}
						}
				),
				eventPacketHandler,
				PacketCollectionIdGenerator.Defaults.UNIQUE_GENERATOR
		);
		PacketListReceiverHandler statusPacketListReceiverHandler = new PacketListReceiverHandler(
				new PacketListReceiverMultiplexer(
						sourceAndFragmentUpdater,
						(packets, wasInstant) -> {
							LOGGER.debug("Debugging all packets");
							try {
								LOGGER.debug(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(packets));
							} catch (JsonProcessingException e) {
								LOGGER.debug("Never mind about that...", e);
							}
						}
				),
				new PacketHandlerMultiplexer(statusPacketHandlers),
				SolarMain.createIdGenerator(options.getUniqueIdsInOneHour())
		);
		List<PacketListReceiver> packetListReceiverList = new ArrayList<>();
		for (DataRequester dataRequester : options.getDataRequesterList()) {
			packetListReceiverList.add(dataRequester.createPacketListReceiver(eventPacketListReceiverHandler.getPacketListReceiverAccepter()));
		}
		packetListReceiverList.addAll(Arrays.asList(
				statusPacketListReceiverHandler.getPacketListReceiverAccepter(),
				statusPacketListReceiverHandler.getPacketListReceiverPacker(),
				eventPacketListReceiverHandler.getPacketListReceiverPacker(),
				statusPacketListReceiverHandler.getPacketListReceiverHandler(),
				eventPacketListReceiverHandler.getPacketListReceiverHandler()
		));
		PacketListReceiver packetListReceiver = new PacketListReceiverMultiplexer(packetListReceiverList);
		try {
			while (!Thread.currentThread().isInterrupted()) {
				List<Packet> packets = new ArrayList<>();
				packetListReceiver.receive(packets, true);
				Thread.sleep(5000);
			}
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
		LOGGER.info("Ending program. Must have been interrupted.");
		return 0;
	}

}
