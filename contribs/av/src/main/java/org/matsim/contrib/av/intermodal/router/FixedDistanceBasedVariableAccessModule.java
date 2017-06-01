/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2016 by the members listed in the COPYING,        *
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

/**
 * 
 */
package org.matsim.contrib.av.intermodal.router;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Route;
import org.matsim.contrib.av.intermodal.router.config.VariableAccessConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.routes.GenericRouteImpl;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.opengis.feature.simple.SimpleFeature;

import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKTReader;

/**
 * @author  jbischoff
 *
 */
/**
 *
 */
public class FixedDistanceBasedVariableAccessModule implements VariableAccessEgressTravelDisutility {

	
	private Map<String,Boolean> teleportedModes = new HashMap<>();
	private Map<Integer,String> distanceMode = new TreeMap<>();
	private Map<String, Double> minXVariableAccessArea = new HashMap<>();
	private Map<String, Double> minYVariableAccessArea = new HashMap<>();
	private Map<String, Double> maxXVariableAccessArea = new HashMap<>();
	private Map<String, Double> maxYVariableAccessArea = new HashMap<>();
	private Map<String, Geometry> geometriesVariableAccessArea = new HashMap<>();
	 // surcharge only applied to trips starting or ending in the variable access area
	private Map<Coord, Double> discouragedTransitStopCoord2TimeSurcharge = new HashMap<>();
	
	private final Network carnetwork;
	private final Config config;
	
	/**
	 * 
	 */
	public FixedDistanceBasedVariableAccessModule(Network carnetwork, Config config) {
		this.config = config;
		this.carnetwork = carnetwork;
		VariableAccessConfigGroup vaconfig = (VariableAccessConfigGroup) config.getModules().get(VariableAccessConfigGroup.GROUPNAME);
		if(vaconfig.getVariableAccessAreaShpFile() != null && vaconfig.getVariableAccessAreaShpKey() != null){
			geometriesVariableAccessArea = readShapeFileAndExtractGeometry(vaconfig.getVariableAccessAreaShpFile(), vaconfig.getVariableAccessAreaShpKey());
			for(String name: geometriesVariableAccessArea.keySet()){
				Envelope e = geometriesVariableAccessArea.get(name).getEnvelopeInternal();
				minXVariableAccessArea.put(name, e.getMinX());
				minYVariableAccessArea.put(name, e.getMinY());
				maxXVariableAccessArea.put(name, e.getMaxX());
				maxYVariableAccessArea.put(name, e.getMaxY());
			}
		}
		teleportedModes.put(TransportMode.transit_walk, true);
		
		// for av scenario Heiligensee, Konradshoehe
		// S25 service every 20 min -> 10 min mean wait time
		discouragedTransitStopCoord2TimeSurcharge.put(new Coord(4584548.0, 5831850.0), 10*60.); // S Schulzendorf
		discouragedTransitStopCoord2TimeSurcharge.put(new Coord(4583339.0, 5833154.0), 10*60.); // S Heiligensee
		// Bus lines across river Havel or Tegeler See -> inaccessible
		// Bus 136
		discouragedTransitStopCoord2TimeSurcharge.put(new Coord(4581591.0, 5832750.0), 10*60*60.);
		discouragedTransitStopCoord2TimeSurcharge.put(new Coord(4581669.0, 5831774.0), 10*60*60.);
		discouragedTransitStopCoord2TimeSurcharge.put(new Coord(4581497.6418, 5831745.5566), 10*60*60.);
		discouragedTransitStopCoord2TimeSurcharge.put(new Coord(4581429.0, 5831400.0), 10*60*60.);
		discouragedTransitStopCoord2TimeSurcharge.put(new Coord(4581312.2374, 5831242.7166), 10*60*60.);
		discouragedTransitStopCoord2TimeSurcharge.put(new Coord(4581260.0, 5830890.0), 10*60*60.);
		discouragedTransitStopCoord2TimeSurcharge.put(new Coord(4581377.0, 5830535.0), 10*60*60.);
		discouragedTransitStopCoord2TimeSurcharge.put(new Coord(4581547.0, 5829967.0), 10*60*60.);
		discouragedTransitStopCoord2TimeSurcharge.put(new Coord(4581923.0, 5828614.0), 10*60*60.);
		discouragedTransitStopCoord2TimeSurcharge.put(new Coord(4582131.0, 5827991.0), 10*60*60.);
		discouragedTransitStopCoord2TimeSurcharge.put(new Coord(4582238.0, 5827622.0), 10*60*60.);
		discouragedTransitStopCoord2TimeSurcharge.put(new Coord(4582314.0, 5827362.0), 20*60.); //ferry to Tegelort
		discouragedTransitStopCoord2TimeSurcharge.put(new Coord(4582293.0, 5827026.0), 10*60*60.);
		discouragedTransitStopCoord2TimeSurcharge.put(new Coord(4582144.0, 5826847.0), 10*60*60.);
		discouragedTransitStopCoord2TimeSurcharge.put(new Coord(4582187.0, 5826715.0), 10*60*60.);
		discouragedTransitStopCoord2TimeSurcharge.put(new Coord(4582100.0, 5826487.0), 10*60*60.);
		discouragedTransitStopCoord2TimeSurcharge.put(new Coord(4582544.0, 5826421.0), 10*60*60.);
		discouragedTransitStopCoord2TimeSurcharge.put(new Coord(4582070.4177, 5826076.2695), 10*60*60.);
		discouragedTransitStopCoord2TimeSurcharge.put(new Coord(4582036.0171, 5825885.0392), 10*60*60.);
		// Bus 139, 236
		discouragedTransitStopCoord2TimeSurcharge.put(new Coord(4582332.0, 5826025.0), 10*60*60.);
		discouragedTransitStopCoord2TimeSurcharge.put(new Coord(4582770.0, 5825930.0), 10*60*60.);
		discouragedTransitStopCoord2TimeSurcharge.put(new Coord(4582760.0, 5825520.0), 10*60*60.);
		discouragedTransitStopCoord2TimeSurcharge.put(new Coord(4583330.0, 5825680.0), 10*60*60.);
		discouragedTransitStopCoord2TimeSurcharge.put(new Coord(4583240.0, 5825340.0), 10*60*60.);
		// Bus 133
		discouragedTransitStopCoord2TimeSurcharge.put(new Coord(4585180.0, 5826171.0), 10*60*60.);
		discouragedTransitStopCoord2TimeSurcharge.put(new Coord(4585408.0, 5826845.0), 10*60*60.);
		discouragedTransitStopCoord2TimeSurcharge.put(new Coord(4585571.0, 5827345.0), 10*60*60.);
		discouragedTransitStopCoord2TimeSurcharge.put(new Coord(4585857.0, 5827693.0), 10*60*60.);
		discouragedTransitStopCoord2TimeSurcharge.put(new Coord(4586403.0, 5827736.0), 10*60*60.);
	}
	/**
	 * 
	 * @param mode the mode to register
	 * @param maximumAccessDistance maximum beeline Distance for using this mode
	 * @param isTeleported defines whether this is a teleported mode
	 * @param lcp for non teleported modes, some travel time assumption is required
	 */
	public void registerMode(String mode, int maximumAccessDistance, boolean isTeleported){
		if (this.distanceMode.containsKey(maximumAccessDistance)){
			throw new RuntimeException("Maximum distance of "+maximumAccessDistance+" is already registered to mode "+distanceMode.get(maximumAccessDistance)+" and cannot be re-registered to mode: "+mode);
		}
		if (isTeleported){
			teleportedModes.put(mode, true);
			distanceMode.put(maximumAccessDistance, mode);
		} else {
			teleportedModes.put(mode, false);
			distanceMode.put(maximumAccessDistance,mode);
			
			
		}
	}
	
	
	/* (non-Javadoc)
	 * @see playground.jbischoff.pt.VariableAccessEgressTravelDisutility#getAccessEgressModeAndTraveltime(org.matsim.api.core.v01.population.Person, org.matsim.api.core.v01.Coord, org.matsim.api.core.v01.Coord)
	 */
	@Override
	public Leg getAccessEgressModeAndTraveltime(Person person, Coord coord, Coord toCoord, double time) {
		double egressDistance = CoordUtils.calcEuclideanDistance(coord, toCoord);
		// return usual transit walk if the access / egress leg has neither origin nor destination in the area where variable access shall be used
		String mode = TransportMode.transit_walk;
		double discouragedTransitStopTimeSurcharge = 0;
		boolean isStartInVariableAccessArea = isInVariableAccessArea(coord);
		boolean isEndInVariableAccessArea = isInVariableAccessArea(toCoord);
		if (isStartInVariableAccessArea || isEndInVariableAccessArea) {
			if (discouragedTransitStopCoord2TimeSurcharge.containsKey(coord)) {
				discouragedTransitStopTimeSurcharge = discouragedTransitStopCoord2TimeSurcharge.get(coord);
			} else if(discouragedTransitStopCoord2TimeSurcharge.containsKey(toCoord)) {
				discouragedTransitStopTimeSurcharge = discouragedTransitStopCoord2TimeSurcharge.get(toCoord);
			}
		}
		if (isStartInVariableAccessArea && isEndInVariableAccessArea){
			mode = getModeForDistance(egressDistance);
		}
		Leg leg = PopulationUtils.createLeg(mode);
		Link startLink = NetworkUtils.getNearestLink(carnetwork, coord);
		Link endLink = NetworkUtils.getNearestLink(carnetwork, toCoord);
		Route route = new GenericRouteImpl(startLink.getId(),endLink.getId());
		leg.setRoute(route);
		if (this.teleportedModes.get(mode)){
			double distf;
			double speed;
			// RoutingParams for transit_walk are not accessible here, but equal those for access_walk
			if(mode.equals(TransportMode.transit_walk)){
				distf = config.plansCalcRoute().getModeRoutingParams().get(TransportMode.access_walk).getBeelineDistanceFactor();
				speed = config.plansCalcRoute().getModeRoutingParams().get(TransportMode.access_walk).getTeleportedModeSpeed();
			} else {				
				distf = config.plansCalcRoute().getModeRoutingParams().get(mode).getBeelineDistanceFactor();
				speed = config.plansCalcRoute().getModeRoutingParams().get(mode).getTeleportedModeSpeed();
			}
			double distance = egressDistance*distf;
			double travelTime = distance / speed + discouragedTransitStopTimeSurcharge;
			leg.setTravelTime(travelTime);
			route.setDistance(distance);
			leg.setDepartureTime(time);
			
						
		} else {
			double distance = egressDistance*1.3;
			double travelTime = distance / 7.5 + discouragedTransitStopTimeSurcharge;
			leg.setTravelTime(travelTime);
			route.setDistance(distance);
			

		}
		return leg;
	}

	/**
	 * @param egressDistance
	 * @return
	 */
	private String getModeForDistance(double egressDistance) {
		for (Entry<Integer, String> e : this.distanceMode.entrySet()){
			if (e.getKey()>=egressDistance){
//				System.out.println("Mode" + e.getValue()+" "+egressDistance);
				return e.getValue();
			}
		}
		throw new RuntimeException(egressDistance + " m is not covered by any egress / access mode.");
		
	}


	/* (non-Javadoc)
	 * @see playground.jbischoff.pt.VariableAccessEgressTravelDisutility#isTeleportedAccessEgressMode(java.lang.String)
	 */
	@Override
	public boolean isTeleportedAccessEgressMode(String mode) {
		return this.teleportedModes.get(mode);
	}
	
	public static Map<String,Geometry> readShapeFileAndExtractGeometry(String filename, String key){
		Map<String,Geometry> geometry = new HashMap<>();	
		for (SimpleFeature ft : ShapeFileReader.getAllFeatures(filename)) {
			
				GeometryFactory geometryFactory= new GeometryFactory();
				WKTReader wktReader = new WKTReader(geometryFactory);

				try {
					Geometry geo = wktReader.read((ft.getAttribute("the_geom")).toString());
					String lor = ft.getAttribute(key).toString();
					geometry.put(lor, geo);
				} catch (ParseException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}			 
		}	
		return geometry;
	}
	
	private boolean isInVariableAccessArea(Coord coord){
		if(geometriesVariableAccessArea.size() > 0){
			for(String name: geometriesVariableAccessArea.keySet()){
				if(minXVariableAccessArea.get(name) < coord.getX() && maxXVariableAccessArea.get(name) > coord.getX() &&
						minYVariableAccessArea.get(name) < coord.getY() && maxYVariableAccessArea.get(name) > coord.getY()){
					if(geometriesVariableAccessArea.get(name).contains(MGC.coord2Point(coord))){
						return true;
					}
				}
			}
			return false;
		} else {			
			return true;
		}
	}
	

}
