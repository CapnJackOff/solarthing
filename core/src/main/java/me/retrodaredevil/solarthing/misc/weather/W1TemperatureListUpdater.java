package me.retrodaredevil.solarthing.misc.weather;

import me.retrodaredevil.solarthing.SolarThingConstants;
import me.retrodaredevil.solarthing.packets.Packet;
import me.retrodaredevil.solarthing.packets.handling.PacketListReceiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public class W1TemperatureListUpdater implements PacketListReceiver {
	private static final Logger LOGGER = LoggerFactory.getLogger(W1TemperatureListUpdater.class);

	private final File slaveFile;
	private final File nameFile;

	private final int dataId;

	public W1TemperatureListUpdater(File directory, int dataId) {
		slaveFile = new File(directory, "w1_slave");
		nameFile = new File(directory, "name");
		this.dataId = dataId;

		if (!directory.isDirectory()) {
			LOGGER.warn(SolarThingConstants.SUMMARY_MARKER, "file: " + directory + " is not a directory! Program will continue. (Maybe it will connect later)");
		}
	}

	@Override
	public void receive(List<Packet> packets, boolean wasInstant) {
		final String name;
		try {
			name = readContents(nameFile).replaceAll("\n", ""); // remove new line at end
		} catch (IOException e) {
			LOGGER.error("Could not read name file", e);
			return;
		}
		final List<String> lines;
		try {
			lines = Files.readAllLines(slaveFile.toPath(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOGGER.error("Could not read slave file for name=" + name, e);
			return;
		}
		if (lines.size() != 2) {
			LOGGER.warn("lines.size() is " + lines.size() + "! lines={}", lines);
			return;
		}
		String line1 = lines.get(0);
		String line2 = lines.get(1);

		String[] split1 = line1.split(" ");
		String message = split1[split1.length - 1];
		if (!"YES".equals(message)) {
			LOGGER.warn("Unsuccessful message: " + message + " name=" + name);
			return;
		}
		String[] split2 = line2.split("=");
		String temperatureString = split2[split2.length - 1];
		final int temperatureRaw;
		try {
			temperatureRaw = Integer.parseInt(temperatureString);
		} catch (NumberFormatException ex) {
			LOGGER.warn("Unable to parse. line2=" + line2 + " name=" + name, ex);
			return;
		}
		if (temperatureRaw == 0) {
			LOGGER.warn("Temperature is exactly 0! name=" + name);
			return;
		}
		float temperatureCelsius = temperatureRaw / 1000.0f;
		packets.add(new CelsiusTemperaturePacket(dataId, temperatureCelsius));
		LOGGER.debug("Read temperature " + temperatureCelsius + "C from " + name);
	}
	private static String readContents(File file) throws IOException {
		return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
	}
}
