/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2017 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package playground.gleich.av_bus.analysis;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.utils.io.IOUtils;

public class ExperiencedTripsWriter {
	private String path;
	private Map<Id<Person>, List<ExperiencedTrip>> agent2trips;
	private Set<String> monitoredModes;
	private String sep = ",";
	private BufferedWriter bw;
	
	public ExperiencedTripsWriter(String path, Map<Id<Person>, List<ExperiencedTrip>> agent2trips, 
			Set<String> monitoredModes){
		this.path = path;
		this.agent2trips = agent2trips;
		this.monitoredModes = monitoredModes;
		try {
			initialize();
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException("could not initialize writer");
		}
	}
	
	private void initialize() throws IOException {
		bw = IOUtils.getBufferedWriter(path);
		// write header
		bw.write("tripId" + sep + "agent" + sep + "tripNumber" + sep + "activityBefore" + sep +
				"activityAfter" + sep + "fromLinkId" + sep + "toLinkId" + sep +
				"startTime" + sep + "endTime" + sep + "totalTravelTime" + sep + "numberOfLegs");
		for(String mode: monitoredModes){
			bw.write(sep + mode + ".InVehicleTime");
			bw.write(sep + mode + ".Distance");
			bw.write(sep + mode + ".WaitTime");
			bw.write(sep + mode + ".NumberOfLegs");
		}
		bw.write(sep + "Other" + ".InVehicleTime");
		bw.write(sep + "Other" + ".Distance");
		bw.write(sep + "Other" + ".WaitTime");
		bw.write(sep + "Other" + ".NumberOfLegs");
		bw.newLine();
	}
	
	public void writeExperiencedTrips(){
		for(List<ExperiencedTrip> tripList: agent2trips.values()){
			for(ExperiencedTrip trip: tripList){
				writeExperiencedTrip(trip);
			}
		}
	}

	void writeExperiencedTrip(ExperiencedTrip trip) {
		try {
			bw.write(trip.getId() + sep + trip.getAgent() + sep + trip.getTripNumber() + sep +
					trip.getActivityBefore() + sep + trip.getActivityAfter() + sep + 
					trip.getFromLinkId() + sep + trip.getToLinkId() + sep + 
					convertSecondsToTimeString(trip.getStartTime()) + sep + 
					convertSecondsToTimeString(trip.getEndTime()) + sep + 
					trip.getTotalTravelTime() + sep + trip.getLegs().size());
			for(String mode: monitoredModes){
				try{
					bw.write(sep + trip.getMode2inVehicleOrMoveTime().get(mode) + sep + 
							trip.getMode2inVehicleOrMoveDistance().get(mode) + sep +
							trip.getMode2waitTime().get(mode) + sep + trip.getMode2numberOfLegs().get(mode));
				} catch (NullPointerException e){
					e.printStackTrace();
					throw new RuntimeException("monitored mode " + mode +
							" not found in ExperiencedTrip " + trip.getId());
				}
			}
			bw.write(sep + trip.getMode2inVehicleOrMoveTime().get("Other") + sep + 
					trip.getMode2inVehicleOrMoveDistance().get("Other") + sep +
					trip.getMode2waitTime().get("Other") + sep + trip.getMode2numberOfLegs().get("Other"));
			bw.newLine();
		} catch (IOException e1) {
			e1.printStackTrace();
			throw new RuntimeException("could not write");
		}
	}
	
	public static String convertSecondsToTimeString(double seconds) {
		int s = (int) seconds;
		return String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, (s % 60));
	}

	public void closeWriter() {
		try {
			bw.close();
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException("could not close writer");
		}
	}
}
