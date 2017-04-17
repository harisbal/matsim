/* *********************************************************************** *
 * project: org.matsim.*												   *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2008 by the members listed in the COPYING,        *
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

package org.matsim.contrib.bicycle.network;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.io.OsmNetworkReader;
import org.matsim.core.utils.io.UncheckedIOException;
import org.matsim.utils.objectattributes.ObjectAttributes;

/**
 * @author dziemke
 */
public class BicycleOsmNetworkReaderV2 extends OsmNetworkReader {

	private final static Logger log = Logger.getLogger(BicycleOsmNetworkReaderV2.class);

	// ----- Bicycle-specific
	private final static String TAG_CYCLEWAYTYPE= "cycleway";
	private final static String TAG_SURFACE = "surface";
	private final static String TAG_SMOOTHNESS = "smoothness";
	private final static String TAG_BICYCLE = "bicycle";
	private final static String TAG_NOONEWAYBIKE = "oneway:bicycle";
	private ObjectAttributes bikeAttributes = new ObjectAttributes();
	private int countCyclewaytype = 0;
	private int countSurface = 0;
	private int countSmoothness = 0;
	boolean firsttimeParseGeoTiff = true;
	Set<String> modesB = new HashSet<String>();
	List<Long> signalNodes = new ArrayList<Long>();
	List<Long> monitorNodes = new ArrayList<Long>();
	Long currentNodeID = null;
	// -----
	
	private boolean constructingBikeNetwork = true;
	private boolean constructingCarNetwork = true;

	public BicycleOsmNetworkReaderV2(final Network network, final CoordinateTransformation transformation) {
		this(network, transformation, true);
	}

	public BicycleOsmNetworkReaderV2(final Network network, final CoordinateTransformation transformation, final boolean useHighwayDefaults) {
		super(network, transformation, useHighwayDefaults);
		
		if (useHighwayDefaults) {
			log.info("Also falling back to bicycle-specific default values.");
			// ----- Bicycle-specific
			//highway=    ( http://wiki.openstreetmap.org/wiki/Key:highway )
			this.setHighwayDefaults(7, "track",			 1,  10.0/3.6, 1.0,  50);	
			this.setHighwayDefaults(7, "cycleway",		 1,  10.0/3.6, 1.0,  50);

			this.setHighwayDefaults(8, "service",		 1,  10.0/3.6, 1.0,  50);
			
			//if bicycle=yes/designated they can ride, otherwise freespeed is 8km/h
			this.setHighwayDefaults(8, "path", 		   	 1,  10.0/3.6, 1.0,  50);
			this.setHighwayDefaults(8, "pedestrian", 	 1,  10.0/3.6, 1.0,  50); 
			this.setHighwayDefaults(8, "footway", 		 1,  10.0/3.6, 1.0,  50); 	
			//	this.setHighwayDefaults(10, "steps", 		 1,   2.0/3.6, 1.0,  50);

			///what cyclewaytypes do exist on osm? - lane, track, shared_busway
			// -----
		}
	}
	
	public void parse(final String osmFilename) {
		super.parse(osmFilename);
		stats();
	}

	public void parse(final InputStream stream) throws UncheckedIOException {
		super.parse(stream);
		stats();
	}
	
	private void stats() {
		// ----- Bicycle-specific
		log.info("BikeObjectAttributs for cyclewaytype created: " + countCyclewaytype + " which is " + ((float)countCyclewaytype/this.network.getLinks().size())*100 + "%");
		log.info("BikeObjectAttributs for surface created:      " + countSurface      + " which is " + ((float)countSurface/this.network.getLinks().size())*100 + "%");
		log.info("BikeObjectAttributs for smoothness created:   " + countSmoothness   + " which is " + ((float)countSmoothness/this.network.getLinks().size())*100 + "%");
		// -----
	}
	
	
	@Override
	public void createLink(final Network network, final OsmWay way, final OsmNode fromNode, final OsmNode toNode, 
			final double length) {
		String highway = way.tags.get(TAG_HIGHWAY);

        if ("no".equals(way.tags.get(TAG_ACCESS))) {
             return;
        }
		
		// load defaults
		OsmHighwayDefaults defaults = this.highwayDefaults.get(highway);
		if (defaults == null) {
			this.unknownHighways.add(highway);
			return;
		}

		double nofLanes = defaults.lanesPerDirection;
		double laneCapacity = defaults.laneCapacity;
		double freespeed = defaults.freespeed;
		double freespeedFactor = defaults.freespeedFactor;
		boolean oneway = defaults.oneway;
		boolean onewayReverse = false;
		// ----- Bicycle-specific
		boolean onewayReverseBikeallowed = false;
		// -----

		// check if there are tags that overwrite defaults
		// - check tag "junction"
		if ("roundabout".equals(way.tags.get(TAG_JUNCTION))) {
			// if "junction" is not set in tags, get() returns null and equals() evaluates to false
			oneway = true;
		}

		// check tag "oneway"
		String onewayTag = way.tags.get(TAG_ONEWAY);
		if (onewayTag != null) {
			if ("yes".equals(onewayTag)) {
				oneway = true;
			} else if ("true".equals(onewayTag)) {
				oneway = true;
			} else if ("1".equals(onewayTag)) {
				oneway = true;
			} else if ("-1".equals(onewayTag)) {
				onewayReverse = true;
				oneway = false;
			} else if ("no".equals(onewayTag)) {
				oneway = false; // may be used to overwrite defaults
            }
			else {
                log.warn("Could not interpret oneway tag:" + onewayTag + ". Ignoring it.");
			}
		}

		// ----- Bicycle-specific
		// check tag "onewayTagBike" (Wenn eine Einbahnstraße für der Radverkehr geoeffnet ist)
		String noonewayTagBike = way.tags.get(TAG_NOONEWAYBIKE);
		if (noonewayTagBike != null) {
			//System.out.println("onewayTagBike");
			if ("no".equals(noonewayTagBike)) {
				onewayReverseBikeallowed = true;
			} 
			else {
				onewayReverseBikeallowed = false;
				log.warn("Could not interpret oneway tag:" + onewayTag + ". Ignoring it.");
			}
		}
		// -----

        // In case trunks, primary and secondary roads are marked as oneway,
        // the default number of lanes should be two instead of one.
        if(highway.equalsIgnoreCase("trunk") || highway.equalsIgnoreCase("primary") || highway.equalsIgnoreCase("secondary")){
            if((oneway || onewayReverse) && nofLanes == 1.0){
                nofLanes = 2.0;
            }
		}

		String maxspeedTag = way.tags.get(TAG_MAXSPEED);
		if (maxspeedTag != null) {
			try {
				freespeed = Double.parseDouble(maxspeedTag) / 3.6; // convert km/h to m/s
			} catch (NumberFormatException e) {
				if (!this.unknownMaxspeedTags.contains(maxspeedTag)) {
					this.unknownMaxspeedTags.add(maxspeedTag);
					log.warn("Could not parse maxspeed tag:" + e.getMessage() + ". Ignoring it.");
				}
			}
		}

		// check tag "lanes"
		String lanesTag = way.tags.get(TAG_LANES);
		if (lanesTag != null) {
			try {
				double totalNofLanes = Double.parseDouble(lanesTag);
				if (totalNofLanes > 0) {
					nofLanes = totalNofLanes;

					//By default, the OSM lanes tag specifies the total number of lanes in both directions.
					//So if the road is not oneway (onewayReverse), let's distribute them between both directions
					//michalm, jan'16
		            if (!oneway && !onewayReverse) {
		                nofLanes /= 2.;
		            }
				}
			} catch (Exception e) {
				if (!this.unknownLanesTags.contains(lanesTag)) {
					this.unknownLanesTags.add(lanesTag);
					log.warn("Could not parse lanes tag:" + e.getMessage() + ". Ignoring it.");
				}
			}
		}

		// create the link(s)
		double capacity = nofLanes * laneCapacity;

		if (this.scaleMaxSpeed) {
			freespeed = freespeed * freespeedFactor;
		}

		// ----- Bicycle-specific
		String bicycleTag = way.tags.get(TAG_BICYCLE);
		// -----
		// only create link, if both nodes were found, node could be null, since nodes outside a layer were dropped
		Id<Node> fromId = Id.create(fromNode.id, Node.class);
		Id<Node> toId = Id.create(toNode.id, Node.class);
		if(network.getNodes().get(fromId) != null && network.getNodes().get(toId) != null){
			String origId = Long.toString(way.id);


			// ----- Bicycle-specific
			///
			/////////BIKE AND CAR //////
			////hin
			if (!onewayReverse ) {
				if (constructingBikeNetwork) {
					Link bikel = network.getFactory().createLink(Id.create(this.id, Link.class), network.getNodes().get(fromId), network.getNodes().get(toId));
					if (  !((defaults.hierarchy == 1) || (defaults.hierarchy == 2)) && ( //autobahnen raus und schnellstarassen
							(defaults.hierarchy != 8) || 
							(defaults.hierarchy == 8) && (bicycleTag!= null && (bicycleTag.equals("yes") || bicycleTag.equals("designated")))
							)){ 
						bikel.setLength(length);
						bikel.setFreespeed(getBikeFreespeed(way, fromNode, toNode, length, true, this.id));
						bikel.setCapacity(capacity);
						bikel.setNumberOfLanes(nofLanes);
						modesB.add("bike");
						bikel.setAllowedModes(modesB);
						if (bikel instanceof Link) {
							final String id1 = origId;
							NetworkUtils.setOrigId( ((Link) bikel), id1 ) ;
						}
						network.addLink(bikel);
						bikeLinkAtts(way, fromNode, toNode, length, true, this.id);
				
					}	
				}
			
				if (constructingCarNetwork) {
					if (defaults.hierarchy == 6 || defaults.hierarchy == 7 || defaults.hierarchy == 8) {
						//do nothing
					} else {
						Link l = network.getFactory().createLink(Id.create(this.id+"_car", Link.class), network.getNodes().get(fromId), network.getNodes().get(toId));
						l.setLength(length);
						l.setFreespeed(freespeed);
						l.setCapacity(capacity);
						l.setNumberOfLanes(nofLanes);
						if (l instanceof Link) {
							final String id1 = origId;
							NetworkUtils.setOrigId( ((Link) l), id1 ) ;
						}
						network.addLink(l);
					}	
				}
				
				this.id++;
			}
			
			//rueck
			if (!oneway || onewayReverseBikeallowed) {

				if (constructingBikeNetwork) {
					Link bikel = network.getFactory().createLink(Id.create(this.id, Link.class), network.getNodes().get(toId), network.getNodes().get(fromId));
					if (  !((defaults.hierarchy == 1) || (defaults.hierarchy == 2)) && ( //autobahnen raus und schnellstarassen
							(defaults.hierarchy != 8) || 
							(defaults.hierarchy == 8) && (bicycleTag!= null && (bicycleTag.equals("yes") || bicycleTag.equals("designated")))
							)){ 
//					if (  !((defaults.hierarchy == 1) || (defaults.hierarchy == 2))  //autobahnen raus und schnellstarassen
//							){ 
						bikel.setLength(length);
						bikel.setFreespeed(getBikeFreespeed(way, fromNode, toNode, length, false, this.id));
						bikel.setCapacity(capacity);
						bikel.setNumberOfLanes(nofLanes);
						modesB.add("bike");
						bikel.setAllowedModes(modesB);
						if (bikel instanceof Link) {
							final String id1 = origId;
							NetworkUtils.setOrigId( ((Link) bikel), id1 ) ;
						}
						network.addLink(bikel);
						bikeLinkAtts(way, fromNode, toNode, length, false, this.id);
				
					}
				}
			
				if (constructingCarNetwork) {
					//autolink nur wenn straße groß genug
					if (defaults.hierarchy == 6 || defaults.hierarchy == 7 || defaults.hierarchy == 8) {
						//do nothing
					} else {
						Link l = network.getFactory().createLink(Id.create(this.id+"_car", Link.class), network.getNodes().get(toId), network.getNodes().get(fromId));
						l.setLength(length);
						l.setFreespeed(freespeed);
						l.setCapacity(capacity);
						l.setNumberOfLanes(nofLanes);
						if (l instanceof Link) {
							final String id1 = origId;
							NetworkUtils.setOrigId( ((Link) l), id1 ) ;
						}
						network.addLink(l);
					}	
				}
				this.id++;
			}
			// -----
		}
	}

	
	// ----- From here on only bicycle-specific methods ----- ----- ----- ----- -----
	public void constructBikeNetwork(String inputOSM) {
		constructingBikeNetwork = true;
		constructingCarNetwork = false;
		parse(inputOSM);
	}

	
	public void constructCarNetwork(String inputOSM) {
		constructingBikeNetwork = false;
		constructingCarNetwork = true;
		parse(inputOSM);
	}

	
	// good example for setting parameters for bike-routing:
	// https://github.com/graphhopper/graphhopper/blob/master/core/src/main/java/com/graphhopper/routing/util/BikeCommonFlagEncoder.java
	private double getBikeFreespeed(final OsmWay way, final OsmNode fromNode, final OsmNode toNode, final double length, boolean hinweg, long matsimID) {

		double bike_freespeed_highway = 0;
		double bike_freespeed_surface = 0;

		String bicycleTag = way.tags.get(TAG_BICYCLE);



		/// HIGHWAY
		String highwayTag = way.tags.get(TAG_HIGHWAY);
		if (highwayTag != null) {
			switch (highwayTag){
			case "cycleway": 			bike_freespeed_highway= 18; break;

			case "path":
				if (bicycleTag != null) {	
					if (bicycleTag.equals("yes") || bicycleTag.equals("designated")) {		
						bike_freespeed_highway=  15; break;}
					else 
						bike_freespeed_highway=  12; break;}
				else
				{bike_freespeed_highway=  12; break;}
			case "footway": 
				if (bicycleTag != null) {	
					if (bicycleTag.equals("yes") || bicycleTag.equals("designated")) {		
						bike_freespeed_highway=  15; break;}
					else 
						bike_freespeed_highway=  8; break;}
				else
				{bike_freespeed_highway=  8; break;}
			case "pedestrian":
				if (bicycleTag != null) {	
					if (bicycleTag.equals("yes") || bicycleTag.equals("designated")) {		
						bike_freespeed_highway=  15; break;}
					else 
						bike_freespeed_highway=  8; break;}
				else
				{bike_freespeed_highway=  8; break;}
			case "track": 				bike_freespeed_highway= 12; break; 
			case "service": 			bike_freespeed_highway= 14; break; 
			case "residential":			bike_freespeed_highway= 18; break;
			case "minor":				bike_freespeed_highway= 16; break;

			case "unclassified":		bike_freespeed_highway= 16; break;  // if no other highway applies
			case "road": 				bike_freespeed_highway= 12; break;  // unknown road

			//				case "trunk": 				bike_freespeed_highway= 18; break;  // shouldnt be used by bikes anyways
			//				case "trunk_link":			bike_freespeed_highway= 18; break; 	// shouldnt be used by bikes anyways
			case "primary": 			bike_freespeed_highway= 18; break; 
			case "primary_link":		bike_freespeed_highway= 18; break; 
			case "secondary":			bike_freespeed_highway= 18; break; 
			case "secondary_link":		bike_freespeed_highway= 18; break; 
			case "tertiary": 			bike_freespeed_highway= 18; break;	 
			case "tertiary_link":		bike_freespeed_highway= 18; break; 
			case "living_street":		bike_freespeed_highway= 14; break;
			//	case "steps":				bike_freespeed_highway=  2; break; //should steps be added??
			default: 					bike_freespeed_highway=  14; log.info(highwayTag + " highwayTag not recognized");
			}
		}
		else {
			bike_freespeed_highway= 14;
			log.info("no highway info");
		}
		//		TODO http://wiki.openstreetmap.org/wiki/DE:Key:tracktype		
		//		TrackTypeSpeed("grade1", 18); // paved
		//      TrackTypeSpeed("grade2", 12); // now unpaved ...
		//      TrackTypeSpeed("grade3", 8);
		//      TrackTypeSpeed("grade4", 6);
		//      TrackTypeSpeed("grade5", 4); // like sand/grass   


		// 	TODO may be useful to combine with smoothness-tag
		/// SURFACE
		String surfaceTag = way.tags.get(TAG_SURFACE);
		if (surfaceTag != null) {
			switch (surfaceTag){
			case "paved": 					bike_freespeed_surface=  18; break;
			case "asphalt": 				bike_freespeed_surface=  18; break;
			case "cobblestone":				bike_freespeed_surface=   9; break;
			case "cobblestone (bad)":		bike_freespeed_surface=   8; break;
			case "cobblestone;flattened":
			case "cobblestone:flattened": 	bike_freespeed_surface=  10; break;
			case "sett":					bike_freespeed_surface=  10; break;

			case "concrete": 				bike_freespeed_surface=  18; break;
			case "concrete:lanes": 			bike_freespeed_surface=  16; break;
			case "concrete_plates":
			case "concrete:plates": 		bike_freespeed_surface=  16; break;
			case "paving_stones": 			bike_freespeed_surface=  12; break;
			case "paving_stones:35": 
			case "paving_stones:30": 		bike_freespeed_surface=  12; break;

			case "unpaved": 				bike_freespeed_surface=  14; break;
			case "compacted": 				bike_freespeed_surface=  16; break;
			case "dirt": 					bike_freespeed_surface=  10; break;
			case "earth": 					bike_freespeed_surface=  12; break;
			case "fine_gravel": 			bike_freespeed_surface=  16; break;

			case "gravel": 					bike_freespeed_surface=  10; break;
			case "ground": 					bike_freespeed_surface=  12; break;
			case "wood": 					bike_freespeed_surface=   8; break;
			case "pebblestone": 			bike_freespeed_surface=  16; break;
			case "sand": 					bike_freespeed_surface=   8; break; //very different kinds of sand :(

			case "bricks": 					bike_freespeed_surface=  14; break;
			case "stone": 					bike_freespeed_surface=  14; break;
			case "grass": 					bike_freespeed_surface=   8; break;

			case "compressed": 				bike_freespeed_surface=  14; break; //guter sandbelag
			case "asphalt;paving_stones:35":bike_freespeed_surface=  16; break;
			case "paving_stones:3": 		bike_freespeed_surface=  12; break;

			default: 						bike_freespeed_surface=  14; log.info(surfaceTag + " surface not recognized");
			}		
		} else {
			if (highwayTag != null) {
				if (highwayTag.equals("primary") || highwayTag.equals("primary_link") ||highwayTag.equals("secondary") || highwayTag.equals("secondary_link")) {	
					bike_freespeed_surface= 18;
				} else {
					bike_freespeed_surface = 14;
					//log.info("no surface info");
				}
			}
		}

		//Minimum of surface_speed and highwaytype_speed
		double bike_freespeedMin = Math.min(bike_freespeed_surface, bike_freespeed_highway);


		/// SLOPE
		double slopeTag = getSlope(way, fromNode, toNode, length, hinweg, matsimID);
		double slopeSpeedFactor = 1; 

		if (slopeTag > 0.10) {								//// uphill
			slopeSpeedFactor= 0.1;
		} else if (slopeTag <=  0.10 && slopeTag >  0.05) {		
			slopeSpeedFactor= 0.4;		
		} else if (slopeTag <=  0.05 && slopeTag >  0.03) {
			slopeSpeedFactor= 0.6;	
		} else if (slopeTag <=  0.03 && slopeTag >  0.01) {
			slopeSpeedFactor= 0.8;
		} else if (slopeTag <=  0.01 && slopeTag > -0.01) { //// flat
			slopeSpeedFactor= 1;
		} else if (slopeTag <= -0.01 && slopeTag > -0.03) {	//// downhill
			slopeSpeedFactor= 1.2;
		} else if (slopeTag <= -0.03 && slopeTag > -0.05) {	
			slopeSpeedFactor= 1.3;
		} else if (slopeTag <= -0.05 && slopeTag > -0.10) {	
			slopeSpeedFactor= 1.4;
		} else if (slopeTag <= -0.10) {	
			slopeSpeedFactor= 1.5;
		}

		//bike_freespeed incl. slope und signal
		double bike_freespeed= bike_freespeedMin*slopeSpeedFactor; //*signalSpeedReductionFactor;

		//not slower than 4km/h
		bike_freespeed = Math.max(bike_freespeed, 4.0);
		return bike_freespeed/3.6;
	}

	
	private double getSlope(final OsmWay way, final OsmNode fromNode, final OsmNode toNode, final double length, boolean hinweg, long matsimID) {

		String matsimId = Long.toString(matsimID); 		// MAsim Link ID
		double realSlope= 0;

		ElevationDataParser tiffObject = new ElevationDataParser();
		try {
			double heightFrom = tiffObject.parseGeoTiff(fromNode.coord.getX(), fromNode.coord.getY(), firsttimeParseGeoTiff);
			firsttimeParseGeoTiff = false;
			double heightTo = tiffObject.parseGeoTiff(toNode.coord.getX(), toNode.coord.getY(), firsttimeParseGeoTiff);
			double eleDiff = heightTo - heightFrom;
			double slope = eleDiff/length;

			//for better visualisation
			double avgHeight= (heightFrom+heightTo)/2;
			bikeAttributes.putAttribute(matsimId, "avgHeight", avgHeight);
			//

			if (hinweg){
				realSlope = slope;
				bikeAttributes.putAttribute(matsimId, "eleDiff", eleDiff);
				bikeAttributes.putAttribute(matsimId, "slope", slope);
			} else {
				realSlope = -1*slope;
				bikeAttributes.putAttribute(matsimId, "eleDiff", -1*eleDiff);
				bikeAttributes.putAttribute(matsimId, "slope", realSlope);
			}

		} catch (Exception e) {
			e.printStackTrace();}

		return realSlope;
	}

	
	// schreiben der Bike-Attribute: wichtig fuer Disutility und Visualisierung
	private void bikeLinkAtts(final OsmWay way, final OsmNode fromNode, final OsmNode toNode, final double length, boolean hinweg, long matsimID) {

		String matsimId = Long.toString(matsimID); 		// MAsim Link ID
		//		String orgOSMId = Long.toString(way.id);	// Original OSM LinkID


		// cyclewaytype
		String cyclewaytypeTag = way.tags.get(TAG_CYCLEWAYTYPE);
		if (cyclewaytypeTag != null) {
			bikeAttributes.putAttribute(matsimId, "cyclewaytype", cyclewaytypeTag);
			countCyclewaytype++;
		};

		//highwaytype
		String highwayTag = way.tags.get(TAG_HIGHWAY);
		if (highwayTag != null) {
			bikeAttributes.putAttribute(matsimId, "highway", highwayTag);
			//countHighway++;
		};

		//surfacetype
		String surfaceTag = way.tags.get(TAG_SURFACE);
		if (surfaceTag != null) {
			bikeAttributes.putAttribute(matsimId, "surface", surfaceTag);
			countSurface++;
		};
		//osm defaeult for prim and sec highways is asphalt
		if ((surfaceTag != null) && (highwayTag.equals("primary") || highwayTag.equals("primary_link") || highwayTag.equals("secondary") || highwayTag.equals("secondary_link"))){
			bikeAttributes.putAttribute(matsimId, "surface", "asphalt");
			countSurface++;
		};

		//smoothness
		String smoothnessTag = way.tags.get(TAG_SMOOTHNESS);
		if (smoothnessTag != null) {
			bikeAttributes.putAttribute(matsimId, "smoothness", smoothnessTag);
			countSmoothness++;
		};

		//bicycleTag
		String bicycleTag = way.tags.get(TAG_BICYCLE);
		if (bicycleTag != null) {
			bikeAttributes.putAttribute(matsimId, "bicycleTag", bicycleTag);
			//countHighway++;
		};
	}

	
	public ObjectAttributes getBikeAttributes() {
		return this.bikeAttributes;
	}
}