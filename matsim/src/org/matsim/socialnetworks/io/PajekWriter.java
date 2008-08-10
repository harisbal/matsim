/* *********************************************************************** *
 * project: org.matsim.*
 * PajekWriter1.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
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

package org.matsim.socialnetworks.io;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.matsim.basic.v01.Id;
import org.matsim.facilities.Facilities;
import org.matsim.gbl.Gbl;
import org.matsim.population.Act;
import org.matsim.population.Knowledge;
import org.matsim.population.Person;
import org.matsim.population.Population;
import org.matsim.socialnetworks.algorithms.FacilitiesFindScenarioMinMaxCoords;
import org.matsim.socialnetworks.socialnet.SocialNetEdge;
import org.matsim.socialnetworks.socialnet.SocialNetwork;
import org.matsim.socialnetworks.statistics.GeoStatistics;
import org.matsim.utils.geometry.Coord;
import org.matsim.world.Location;
import org.matsim.world.Zone;

import edu.uci.ics.jung.graph.Edge;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.Vertex;



public class PajekWriter {

	private Coord minCoord;
	private Coord maxCoord;
	private TreeMap<Id, Integer> pajekIndex= new TreeMap<Id, Integer>();
	String dir;

	private final static Logger log = Logger.getLogger(PajekWriter.class);

	public PajekWriter(String dir, Facilities facilities){
		this.dir=dir;
		File pjDir=new File(dir+"pajek/");
		log.info("PajekWriter1 make dir "+dir + "pajek/");
		if(!(pjDir.mkdir())&& !pjDir.exists()){
			Gbl.errorMsg("Cannot create directory "+dir+"pajek/");
		}
		log.info("is a dumb writer for UNDIRECTED nets. Replace it with something that iterates through Persons and call it from SocialNetworksTest.");
		FacilitiesFindScenarioMinMaxCoords fff= new FacilitiesFindScenarioMinMaxCoords();
		fff.run(facilities);
		this.minCoord = fff.getMinCoord();
		this.maxCoord = fff.getMaxCoord();
		log.info(" PW X_Max ="+this.maxCoord.getX());
		log.info(" PW Y_Max ="+this.maxCoord.getY());
		log.info(" PW X_Min ="+this.minCoord.getX());
		log.info(" PW Y_Min ="+this.minCoord.getY());

	}

	public void write(ArrayList<SocialNetEdge> links, Population plans, int iter) {
		BufferedWriter pjnet = null;

		// from config

		String pjnetfile = this.dir+"pajek/test"+iter+".net";
		log.info("PajekWriter1 filename "+pjnetfile);

		try {

			pjnet = new BufferedWriter(new FileWriter(pjnetfile));
			log.info(" Successfully opened pjnetfile "+pjnetfile);

		} catch (final IOException ex) {
			ex.printStackTrace();
			return;
		}

		int numPersons = plans.getPersons().values().size();

		try {
//			System.out.print(" *Vertices " + numPersons + " \n");
			pjnet.write("*Vertices " + numPersons+"\r\n");

			Iterator<Person> itPerson = plans.getPersons().values().iterator();
			int iperson = 1;
			while (itPerson.hasNext()) {
				Person p = itPerson.next();
				final Knowledge know = p.getKnowledge();
				if (know == null) {
					Gbl.errorMsg("Knowledge is not defined!");
				}
				Coord xy = ((Act) p.getSelectedPlan().getActsLegs().get(0)).getCoord();
				double x=(xy.getX()-this.minCoord.getX())/(this.maxCoord.getX()-this.minCoord.getX());
				double y=(xy.getY()-this.minCoord.getY())/(this.maxCoord.getY()-this.minCoord.getY());
				pjnet.write(iperson + " \"" + p.getId() + "\" "+x +" "+y+"\r\n");

//				log.info(iperson + " " + p.getId() + " ["+xy.getX() +" "+xy.getY()+"]\n");
				this.pajekIndex.put(p.getId(),iperson);
				iperson++;

			}
			pjnet.write("*Edges\r\n");

//			log.info("*Edges\n");
			Iterator<SocialNetEdge> itLink = links.iterator();
			while (itLink.hasNext()) {
				SocialNetEdge printLink = itLink.next();
				int age = iter-printLink.getTimeLastUsed();
				Person printPerson1 = printLink.person1;
				Person printPerson2 = printLink.person2;

				Coord xy1 = ((Act) printPerson1.getSelectedPlan().getActsLegs().get(0)).getCoord();
				Coord xy2 = ((Act) printPerson2.getSelectedPlan().getActsLegs().get(0)).getCoord();
				double dist = xy1.calcDistance(xy2);

				pjnet.write(" " + this.pajekIndex.get(printPerson1.getId()) + " "+ this.pajekIndex.get(printPerson2.getId())+" "+dist+" "+age+"\r\n");
//				pjnet.write(" " + printPerson1.getId() + " "+ printPerson2.getId());

//				System.out.print(" " +iter+" "+printLink.getLinkId()+" "+ printPerson1.getId() + " "
//				+ printPerson2.getId() + " "
//				+ printLink.getTimeLastUsed()+"\n");
			}

		} catch (IOException ex1) {
			ex1.printStackTrace();
		}

		try {
			pjnet.close();
			log.info(" Successfully closed pjnetfile "+pjnetfile);
		} catch (IOException ex2) {
			ex2.printStackTrace();
		}
		//}
	}
	public void writeGeo(Population plans, SocialNetwork snet, int iter) {

		GeoStatistics gstat = new GeoStatistics(plans, snet);
		Graph g = gstat.makeJungGraph();

		BufferedWriter pjnet = null;

		// from config

		String pjnetfile = this.dir+"pajek/testGeo"+iter+".net";
		log.info("PajekWriter1 Geofilename "+pjnetfile);

		try {

			pjnet = new BufferedWriter(new FileWriter(pjnetfile));
			log.info(" Successfully opened pjnetfile "+pjnetfile);

		} catch (final IOException ex) {
			ex.printStackTrace();
			return;
		}

		int numVertices = g.numVertices();

		try {
			log.info("##### Write Geoaggregated Social Network Output");
			log.info(" *Vertices " + numVertices + " \n");
			pjnet.write("*Vertices " + numVertices+"\r\n");

			Iterator<Vertex> iVert = g.getVertices().iterator();
			HashMap<Vertex,Location> vertLoc = gstat.getVertexLoc();

			int vertexcounter = 1;
			while (iVert.hasNext()) {
				Vertex v = iVert.next();
				Zone zone = (Zone) vertLoc.get(v);

				Coord xy = zone.getCenter();
				double x=(xy.getX()-this.minCoord.getX())/(this.maxCoord.getX()-this.minCoord.getX());
				double y=(xy.getY()-this.minCoord.getY())/(this.maxCoord.getY()-this.minCoord.getY());
				pjnet.write(vertexcounter + " \"" + zone.getId() + "\" "+x +" "+y+"\r\n");

//				System.out.print(iperson + " " + p.getId() + " ["+xy.getX() +" "+xy.getY()+"]\n");
				this.pajekIndex.put(zone.getId(),vertexcounter);
				vertexcounter++;

			}
			pjnet.write("*Edges\r\n");

//			System.out.print("*Edges\n");
			Iterator<Edge> itLink = g.getEdges().iterator();
			while (itLink.hasNext()) {
				Edge printLink = itLink.next();
				Location aLoc = gstat.getVertexLoc().get(printLink.getEndpoints().getFirst());
				Location bLoc = gstat.getVertexLoc().get(printLink.getEndpoints().getSecond());

//				Coord xy1 = (Coord) aLoc.getCenter();
//				Coord xy2 = (Coord) bLoc.getCenter();
//				double dist = xy1.calcDistance(xy2);
				double strength = (Double)printLink.getUserDatum("strength");
//				double strength = gstat.getEdgeStrength().get(printLink);
				pjnet.write(" " + this.pajekIndex.get(aLoc.getId()) + " "+ this.pajekIndex.get(bLoc.getId())+" "+strength+"\r\n");

			}

		} catch (IOException ex1) {
			ex1.printStackTrace();
		}
		try {
			pjnet.close();
			log.info(" Successfully closed pjnetfile "+pjnetfile);
		} catch (IOException ex2) {
			ex2.printStackTrace();
		}

		// Write out a Pajek vector file of the geographically aggregated population for
		// normalizing the graphs in the Pajek display and other analyses

		BufferedWriter pjvec1 = null;
		BufferedWriter pjvec2 = null;
		// from config

		String pjvec1file = this.dir+"pajek/testGeo"+iter+".vec";
		log.info("PajekWriter1 Geofilename "+pjvec1file);
		String pjvec2file = this.dir+"pajek/testGeoDegPop"+iter+".vec";
		log.info("PajekWriter1 Geofilename "+pjvec2file);


		try {

			pjvec1 = new BufferedWriter(new FileWriter(pjvec1file));
			log.info(" Successfully opened pjvecfile "+pjvec1file);

			pjvec2 = new BufferedWriter(new FileWriter(pjvec2file));
			log.info(" Successfully opened pjvecfile "+pjvec2file);

		} catch (final IOException ex) {
			ex.printStackTrace();
			return;
		}

		try {
			//Writes a *.vec file with the population of each geo-zone
			log.info("##### Write Geoaggregated Population Vector");
			log.info(" *Vertices " + numVertices + " \n");
			pjvec1.write("*Vertices " + numVertices+"\r\n");

			log.info("##### Write Geoaggregated Degree/Population Vector");
			log.info(" *Vertices " + numVertices + " \n");
			pjvec2.write("*Vertices " + numVertices+"\r\n");

			Iterator<Vertex> iVert = g.getVertices().iterator();
			while (iVert.hasNext()) {
				Vertex v = iVert.next();
				int pop = (Integer) v.getUserDatum("population");
				int deg = v.degree();
				pjvec1.write( pop + "\r\n");
				pjvec2.write(((double) deg / (double) pop) + "\r\n");
			}



		} catch (IOException ex1) {
			ex1.printStackTrace();
		}
		try {
			pjvec1.close();
			log.info(" Successfully closed pjvecfile "+pjvec1file);
			pjvec2.close();
			log.info(" Successfully closed pjvecfile "+pjvec2file);
		} catch (IOException ex2) {
			ex2.printStackTrace();
		}

	}
}
