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

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;

// Coords unavailable in events
/**
 * @author gleich
 *
 */
public class ExperiencedLeg {
	private final Id<Person> agent;
//	private final Coord from;
//	private final Coord to;
	private final Id<Link> fromLinkId;
	private final Id<Link> toLinkId;
	private final double startTime;
	private final double endTime;
	
	private final String mode;
	private final double waitTime;
	private final double inVehicleTime;
	private final double distance;
	
	ExperiencedLeg(Id<Person> agent,
//			Coord from, Coord to, 
			Id<Link> fromLink, Id<Link> toLink, double startTime,
			double endTime, String mode, double waitTime, double inVehicleTime, double distance) {
		this.agent = agent;
//		this.from = from;
//		this.to = to;
		this.fromLinkId = fromLink;
		this.toLinkId = toLink;
		this.startTime = startTime;
		this.endTime = endTime;
		this.mode = mode;
		this.waitTime = waitTime;
		this.inVehicleTime = inVehicleTime;
		this.distance = distance;
	}
	
	double getDistance() {
		return distance;
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
	String getMode() {
		return mode;
	}
	double getWaitTime() {
		return waitTime;
	}
	double getInVehicleTime() {
		return inVehicleTime;
	}

}
