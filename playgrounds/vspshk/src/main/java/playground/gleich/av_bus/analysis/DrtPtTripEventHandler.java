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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.PersonArrivalEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.PersonArrivalEventHandler;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.TeleportationArrivalEvent;
import org.matsim.core.api.experimental.events.handler.TeleportationArrivalEventHandler;
import org.matsim.vehicles.Vehicle;

// One trip is the sum of all legs and "pt interaction" activities between to real, non-"pt interaction", activities
// Coords unavailable in events -> no coords written
/**
 * @author gleich
 *
 * @param network
 * @param monitoredModes : All trips to be monitored have to consist only of legs of these modes
 * @param monitoredStartAndEndLinks : only trips which start or end on one these links will be monitored.
 * Set to null if you want to have all trips from all origins and to all destinations.
 */
public class DrtPtTripEventHandler implements ActivityStartEventHandler, ActivityEndEventHandler,
PersonDepartureEventHandler, PersonArrivalEventHandler, PersonEntersVehicleEventHandler, 
LinkEnterEventHandler, TeleportationArrivalEventHandler {
	
//	private Set<Id<Person>> agentsOnMonitoredTrip = new HashSet<>(); -> agent2CurrentTripStartLink.contains()
//	private Map<Id<Person>, Boolean> agentHasDrtLeg = new HashMap<>();
	private Network network;
	
	private Map<Id<Person>, List<ExperiencedTrip>> person2ExperiencedTrips = new HashMap<>();
	
//	private Map<Id<Person>, Coord> agent2CurrentTripStartCoord = new HashMap<>();
	private Map<Id<Person>, String> agent2CurrentTripActivityBefore = new HashMap<>();
	private Map<Id<Person>, Id<Link>> agent2CurrentTripStartLink = new HashMap<>();
	private Map<Id<Person>, Double> agent2CurrentTripStartTime = new HashMap<>();
	private Map<Id<Person>, List<ExperiencedLeg>> agent2CurrentTripExperiencedLegs = new HashMap<>();
	
//	private Map<Id<Person>, Coord> agent2CurrentLegStartCoord = new HashMap<>();
	private Map<Id<Person>, Id<Link>> agent2CurrentLegStartLink = new HashMap<>();
	private Map<Id<Person>, Double> agent2CurrentLegStartTime = new HashMap<>();
	private Map<Id<Person>, Double> agent2CurrentLegEnterVehicleTime = new HashMap<>();
	private Map<Id<Person>, Double> agent2CurrentLegDistanceOffsetAtEnteringVehicle = new HashMap<>();
	private Map<Id<Person>, Id<Vehicle>> agent2CurrentLegVehicle = new HashMap<>();
	private Map<Id<Person>, Double> agent2CurrentTeleportDistance = new HashMap<>();
	
	private Map<Id<Vehicle>, Double> monitoredVeh2toMonitoredDistance = new HashMap<>();
	private Set<String> monitoredModes = new HashSet<>();
	private Set<Id<Link>> monitoredStartAndEndLinks = new HashSet<>(); // set to null if all links are to be monitored
	
	/**
	 * 
	 * @param network
	 * @param monitoredModes: All trips to be monitored have to consist only of legs of these modes
	 * @param monitoredStartAndEndLinks: only trips which start or end on one these links will be monitored. Set to null if you want to have all trips from all origins and to all destinations
	 */
	public DrtPtTripEventHandler(Network network, Set<String> monitoredModes, Set<Id<Link>> monitoredStartAndEndLinks){
		this.network = network;
		this.monitoredModes = monitoredModes; // pt, transit_walk, drt: walk eigentlich nicht, aber in FixedDistanceBased falsch als walk statt transit_walk gesetzt
		this.monitoredStartAndEndLinks = monitoredStartAndEndLinks;
	}

	@Override
	public void reset(int iteration) {
		// TODO Auto-generated method stub
		
	}

	// in-vehicle distances
	@Override
	public void handleEvent(LinkEnterEvent event) {
		if(monitoredVeh2toMonitoredDistance.containsKey(event.getVehicleId())) {
			monitoredVeh2toMonitoredDistance.put(event.getVehicleId(), 
					monitoredVeh2toMonitoredDistance.get(event.getVehicleId()) + network.getLinks().get(event.getLinkId()).getLength());
		}
	}

	/*
	 * Save the activity type of the last activity before the trip. We cannot know yet if this is a trip to be monitored or not
	 * (leg mode and arrival link are unknown), so save this for all agents.
	 */
	@Override
	public void handleEvent(ActivityEndEvent event) {
		if(!event.getActType().equals("pt interaction")){
			agent2CurrentTripActivityBefore.put(event.getPersonId(), event.getActType());			
		}
	}
	
	// Detect start of a leg (and possibly the start of a trip)
	@Override
	public void handleEvent(PersonDepartureEvent event) {
		/* 
		 * if a trip includes a leg of a mode not contained in monitoredModes, this lead to NullPointerExceptions at 
		 * handleEvent(PersonArrivalEvent event). This can be avoided by removing the following check of the leg mode,
		 * however in this case all legs of all modes will be saved and later while saving ExperiencedTrips in
		 * handleEvent(ActivityStartEvent event) those trips not containing would have to be filtered out.
		 */
		if(!monitoredModes.contains(event.getLegMode())) {
			return;
		} else {
			if(!agent2CurrentTripStartLink.containsKey(event.getPersonId())) {
				agent2CurrentTripStartLink.put(event.getPersonId(), event.getLinkId());
				agent2CurrentTripStartTime.put(event.getPersonId(), event.getTime());
				agent2CurrentTripExperiencedLegs.put(event.getPersonId(), new ArrayList<>());
			}
			agent2CurrentLegStartLink.put(event.getPersonId(), event.getLinkId());
			agent2CurrentLegStartTime.put(event.getPersonId(), event.getTime());
		}
	}
	
	// Detect end of wait time and begin of in-vehicle time, monitor used vehicle to count in-vehicle distance
	@Override
	public void handleEvent(PersonEntersVehicleEvent event) {
		if(agent2CurrentTripStartLink.containsKey(event.getPersonId())){			
			agent2CurrentLegVehicle.put(event.getPersonId(), event.getVehicleId());
			agent2CurrentLegEnterVehicleTime.put(event.getPersonId(), event.getTime());
			if(monitoredVeh2toMonitoredDistance.containsKey(event.getVehicleId())) {
				agent2CurrentLegDistanceOffsetAtEnteringVehicle.put(event.getPersonId(), 
						monitoredVeh2toMonitoredDistance.get(event.getVehicleId()));
			} else {
				agent2CurrentLegDistanceOffsetAtEnteringVehicle.put(event.getPersonId(), 0.0);
				// -> start monitoring the vehicle
				monitoredVeh2toMonitoredDistance.put(event.getVehicleId(), 0.0);
			}
		} else {
			return;
		}
	}
	
	// teleport walk distances
	@Override
	public void handleEvent(TeleportationArrivalEvent event) {
		if(agent2CurrentTripStartLink.containsKey(event.getPersonId())){
			// the event should(?!) give the total distance walked -> agent2CurrentTeleportDistance should not contain the agent yet
			agent2CurrentTeleportDistance.put(event.getPersonId(), event.getDistance());
		}
	}
	
	// Detect end of a leg
	@Override
	public void handleEvent(PersonArrivalEvent event) {
		if(agent2CurrentTripStartLink.containsKey(event.getPersonId())){
			double waitTime;
			double inVehicleTime;
			double distance;
			// e.g. pt leg
			if(agent2CurrentLegEnterVehicleTime.containsKey(event.getPersonId())) {
				waitTime = agent2CurrentLegEnterVehicleTime.get(event.getPersonId()) -
						agent2CurrentLegStartTime.get(event.getPersonId());
				inVehicleTime = event.getTime() - agent2CurrentLegEnterVehicleTime.get(event.getPersonId());
				distance = monitoredVeh2toMonitoredDistance.get(agent2CurrentLegVehicle.get(event.getPersonId())) - 
						agent2CurrentLegDistanceOffsetAtEnteringVehicle.get(event.getPersonId());
			// e.g. walk leg
			} else {
				waitTime = 0.0;
				inVehicleTime = event.getTime() - agent2CurrentLegStartTime.get(event.getPersonId());
				distance = agent2CurrentTeleportDistance.containsKey(event.getPersonId()) ? 
						agent2CurrentTeleportDistance.get(event.getPersonId()) : null;
			}
			// Save ExperiencedLeg and remove temporary data
			agent2CurrentTripExperiencedLegs.get(event.getPersonId()).add(new ExperiencedLeg(
					event.getPersonId(), agent2CurrentLegStartLink.get(event.getPersonId()), 
					event.getLinkId(), (double) agent2CurrentLegStartTime.get(event.getPersonId()), 
					event.getTime(), event.getLegMode(), waitTime, inVehicleTime, distance));
			agent2CurrentLegStartLink.remove(event.getPersonId());
			agent2CurrentLegStartTime.remove(event.getPersonId());
			agent2CurrentLegEnterVehicleTime.remove(event.getPersonId());
			agent2CurrentLegDistanceOffsetAtEnteringVehicle.remove(event.getPersonId());
			agent2CurrentLegVehicle.remove(event.getPersonId());
			agent2CurrentTeleportDistance.remove(event.getPersonId());
		}	
	}
//Test
	int tripCounter = 0;
	
	// Detect end of a trip
	@Override
	public void handleEvent(ActivityStartEvent event) {
		if(agent2CurrentTripStartLink.containsKey(event.getPersonId())){
			// Check if this a real activity or whether the trip will continue with another leg after an "pt interaction"
			if(!event.getActType().equals("pt interaction")){				
				//Check if trip starts or ends in the monitored area, that means on the monitored start and end links
				//monitoredStartAndEndLinks=null -> all links are to be monitored
				if(monitoredStartAndEndLinks == null || 
						monitoredStartAndEndLinks.contains(event.getLinkId()) ||
						monitoredStartAndEndLinks.contains(agent2CurrentTripStartLink.get(event.getPersonId()))){
					if(!person2ExperiencedTrips.containsKey(event.getPersonId())){
						person2ExperiencedTrips.put(event.getPersonId(), new ArrayList<>());
					}
					// Save ExperiencedTrip and remove temporary data
					person2ExperiencedTrips.get(event.getPersonId()).add(new ExperiencedTrip(
							event.getPersonId(), agent2CurrentTripActivityBefore.get(event.getPersonId()), event.getActType(),
							agent2CurrentTripStartLink.get(event.getPersonId()), event.getLinkId(),
							agent2CurrentTripStartTime.get(event.getPersonId()), event.getTime(),
							/* events are read in chronological order -> trips are found in chronological order
							 * -> save chronological tripNumber for identification of trips 
							 */
							person2ExperiencedTrips.get(event.getPersonId()).size() + 1,
							agent2CurrentTripExperiencedLegs.get(event.getPersonId()), monitoredModes));
					tripCounter++;
					System.out.println("ExperiencedTrip " + tripCounter);
				}
				agent2CurrentTripStartTime.remove(event.getPersonId());
				agent2CurrentTripStartLink.remove(event.getPersonId());
				agent2CurrentTripExperiencedLegs.remove(event.getPersonId());
			}
		}
	}

	// Getter
	public Map<Id<Person>, List<ExperiencedTrip>> getPerson2ExperiencedTrips() {
		return person2ExperiencedTrips;
	}

	public Set<String> getMonitoredModes() {
		return monitoredModes;
	}

	public Set<Id<Link>> getMonitoredStartAndEndLinks() {
		return monitoredStartAndEndLinks;
	}

}
