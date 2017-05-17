/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2015 by the members listed in the COPYING,        *
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;

/**
 * @author gleich
 *
 */
public class ExperiencedTrip {
	private final Id<Person> agent;
//	private final Coord from;
//	private final Coord to;
	private final Id<Link> fromLinkId;
	private final Id<Link> toLinkId;
	private final double startTime;
	private final double endTime;
	private final List<ExperiencedLeg> legs;
	
	private Map<String, Double> mode2inVehicleOrMoveTime = new HashMap<>();
	private Map<String, Double> mode2inVehicleOrMoveDistance = new HashMap<>();
	private Map<String, Double> mode2waitTime = new HashMap<>();
	
	// Coords unavailable in events
	/**
	 * @param agent
	 * @param from
	 * @param to
	 * @param fromLink
	 * @param toLink
	 * @param startTime
	 * @param endTime
	 * @param includesDrt
	 */
	ExperiencedTrip(Id<Person> agent, 
//			Coord from, Coord to, 
			Id<Link> fromLink, Id<Link> toLink, double startTime,
			double endTime, List<ExperiencedLeg> legs) {
		this.agent = agent;
//		this.from = from;
//		this.to = to;
		this.fromLinkId = fromLink;
		this.toLinkId = toLink;
		this.startTime = startTime;
		this.endTime = endTime;
		this.legs = legs;
		calcSumsOverAllLegs();
	}
	
	private void calcSumsOverAllLegs(){
		for(ExperiencedLeg leg: legs){
			String mode = leg.getMode();
			if(mode2inVehicleOrMoveTime.containsKey(mode)){
				mode2inVehicleOrMoveTime.put(mode, mode2inVehicleOrMoveTime.get(mode) + leg.getInVehicleTime());	
			} else {
				mode2inVehicleOrMoveTime.put(mode, leg.getInVehicleTime());	
			}
			if(mode2inVehicleOrMoveDistance.containsKey(mode)){
				mode2inVehicleOrMoveDistance.put(mode, mode2inVehicleOrMoveDistance.get(mode) + leg.getDistance());	
			} else {
				mode2inVehicleOrMoveDistance.put(mode, leg.getDistance());	
			}
			if(mode2waitTime.containsKey(mode)){
				mode2waitTime.put(mode, mode2waitTime.get(mode) + leg.getWaitTime());	
			} else {
				mode2waitTime.put(mode, leg.getWaitTime());	
			}
		}
	}
	
	double getTotalTravelTime(){
		return endTime-startTime;
	}
	//Getter
	Map<String, Double> getMode2inVehicleOrMoveTime() {
		return mode2inVehicleOrMoveTime;
	}
	
	Map<String, Double> getMode2inVehicleOrMoveDistance() {
		return mode2inVehicleOrMoveDistance;
	}
	
	Map<String, Double> getMode2waitTime() {
		return mode2waitTime;
	}
	Id<Person> getAgent() {
		return agent;
	}
//	Coord getFrom() {
//		return from;
//	}
//	Coord getTo() {
//		return to;
//	}
	Id<Link> getFromLinkId() {
		return fromLinkId;
	}
	Id<Link> getToLinkId() {
		return toLinkId;
	}
	double getStartTime() {
		return startTime;
	}
	double getEndTime() {
		return endTime;
	}
	List<ExperiencedLeg> getLegs() {
		return legs;
	}
}
