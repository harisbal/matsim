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

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.matsim.api.core.v01.Coord;
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
		String mode = TransportMode.walk;
		if(isInVariableAccessArea(coord) && isInVariableAccessArea(toCoord)){
			mode = getModeForDistance(egressDistance);
		}
		Leg leg = PopulationUtils.createLeg(mode);
		Link startLink = NetworkUtils.getNearestLink(carnetwork, coord);
		Link endLink = NetworkUtils.getNearestLink(carnetwork, toCoord);
		Route route = new GenericRouteImpl(startLink.getId(),endLink.getId());
		leg.setRoute(route);
		if (this.teleportedModes.get(mode)){
			double distf = config.plansCalcRoute().getModeRoutingParams().get(mode).getBeelineDistanceFactor();
			double speed = config.plansCalcRoute().getModeRoutingParams().get(mode).getTeleportedModeSpeed();
			double distance = egressDistance*distf;
			double travelTime = distance / speed;
			leg.setTravelTime(travelTime);
			route.setDistance(distance);
			leg.setDepartureTime(time);
			
						
		} else {
			double distance = egressDistance*1.3;
			double travelTime = distance / 7.25;
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
