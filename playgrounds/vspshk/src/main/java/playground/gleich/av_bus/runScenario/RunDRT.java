package playground.gleich.av_bus.runScenario;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.av.intermodal.router.VariableAccessTransitRouterModule;
import org.matsim.contrib.av.intermodal.router.config.VariableAccessConfigGroup;
import org.matsim.contrib.av.intermodal.router.config.VariableAccessModeConfigGroup;
import org.matsim.contrib.drt.run.DrtConfigConsistencyChecker;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.DrtControlerCreator;
import org.matsim.contrib.dvrp.data.FleetImpl;
import org.matsim.contrib.dvrp.data.file.VehicleReader;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.scenario.ScenarioUtils;

import playground.gleich.av_bus.FilePaths;
import playground.gleich.av_bus.analysis.DrtPtTripEventHandler;
import playground.gleich.av_bus.analysis.ExperiencedTripsWriter;

public class RunDRT {
// Override FixedDistanceBasedVariableAccessModule in order to return taxi only for access/egress trips originating or ending within study area
	public static void main(String[] args) {
		Config config = ConfigUtils.loadConfig(FilePaths.PATH_BASE_DIRECTORY + FilePaths.PATH_CONFIG_BERLIN__10PCT_DRT,
				new DrtConfigGroup(), new DvrpConfigGroup());
		VariableAccessConfigGroup vacfg = new VariableAccessConfigGroup();
		vacfg.setVariableAccessAreaShpFile(FilePaths.PATH_BASE_DIRECTORY + FilePaths.PATH_AV_OPERATION_AREA_SHP);
		vacfg.setVariableAccessAreaShpKey(FilePaths.AV_OPERATION_AREA_SHP_KEY);
		vacfg.setStyle("fixed"); //FixedDistanceBasedVariableAccessModule
		{
			VariableAccessModeConfigGroup drt = new VariableAccessModeConfigGroup();
			drt.setDistance(20000);
			drt.setTeleported(false);
			drt.setMode("drt");
			vacfg.setAccessModeGroup(drt);
		}
		{
			VariableAccessModeConfigGroup walk = new VariableAccessModeConfigGroup();
			walk.setDistance(300);
			walk.setTeleported(true);
			walk.setMode(TransportMode.transit_walk);
			vacfg.setAccessModeGroup(walk);
		}
		config.addModule(vacfg);

		String outputDirectory = FilePaths.PATH_BASE_DIRECTORY + FilePaths.PATH_OUTPUT_BERLIN__10PCT_DRT_50_CAP4;
//		String outputDirectory = FilePaths.PATH_BASE_DIRECTORY + FilePaths.PATH_OUTPUT_BERLIN__10PCT_NULLFALL;
//		String outputDirectory = FilePaths.PATH_BASE_DIRECTORY + FilePaths.PATH_OUTPUT_BERLIN__10PCT
		
		// ScenarioUtils.loadScenario(config) searches files starting at the directory where the config is located
		config.network().setInputFile("../../../../" + FilePaths.PATH_NETWORK_BERLIN__10PCT_DRT_ACCESS_LOOPS);
		config.plans().setInputFile("../../../../" + FilePaths.PATH_POPULATION_BERLIN__10PCT_FILTERED);
//		config.plans().setInputFile("../../../../" + "data/input/Berlin10pct/mod/population.10pct.filtered.6Agents.xml");
//		config.plans().setInputFile("../../../../" + "data/output/Berlin10pct/drt_300m_routing_drt_20ms/DRT_50_Cap4/ITERS/it.0/0.plans.xml.gz");
		config.transit().setVehiclesFile("../../../../" + FilePaths.PATH_TRANSIT_VEHICLES_BERLIN__10PCT);
		config.transit().setTransitScheduleFile("../../../../" + FilePaths.PATH_TRANSIT_SCHEDULE_BERLIN__10PCT_WITHOUT_BUSES_IN_STUDY_AREA);
		config.transitRouter().setSearchRadius(15000);
		config.transitRouter().setExtensionRadius(0);
		config.global().setNumberOfThreads(4);
		config.transitRouter().setDirectWalkFactor(100);

		Scenario scenario = ScenarioUtils.loadScenario(config);
		config.controler().setFirstIteration(0);
		config.controler().setLastIteration(100);
		config.controler().setOutputDirectory(outputDirectory);
//		config.controler().setOutputDirectory(FilePaths.PATH_BASE_DIRECTORY + "data/output/Berlin10pct/6Agents_DRT_10_Cap4");
		config.controler().setWritePlansInterval(10);
		config.qsim().setEndTime(60*60*60); // [geloest durch maximum speed in transit_vehicles-datei: bei Stunde 50:00:00 immer noch 492 Veh unterwegs (nur pt veh., keine Agenten), alle pt-fahrten stark verspätet, da pünktlicher start, aber niedrigere Geschwindigkeit als im Fahrplan geplant]
		config.controler().setWriteEventsInterval(10);
		config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		
		DrtConfigGroup.get(config).setVehiclesFile("../../../../" + FilePaths.PATH_DRT_VEHICLES_50_CAP4_BERLIN__10PCT);
		FleetImpl fleet = new FleetImpl();
		new VehicleReader(scenario.getNetwork(), fleet).readFile(FilePaths.PATH_BASE_DIRECTORY + FilePaths.PATH_DRT_VEHICLES_50_CAP4_BERLIN__10PCT);
		
		config.addConfigConsistencyChecker(new DrtConfigConsistencyChecker());
		config.checkConsistency();
		
		Controler controler = DrtControlerCreator.createControler(config, false);
		controler.addOverridingModule(new VariableAccessTransitRouterModule());
		
		controler.run();
		
		EventsManager events = EventsUtils.createEventsManager();
		Set<String> monitoredModes = new HashSet<>();
		monitoredModes.add(TransportMode.pt);
		monitoredModes.add(TransportMode.transit_walk);
//		monitoredModes.add(TransportMode.walk);
		monitoredModes.add("drt");
		
		Set<Id<Link>> linksInArea = new HashSet<>();
		
		try {
			BufferedReader linksReader = new BufferedReader(new FileReader(
			FilePaths.PATH_BASE_DIRECTORY + FilePaths.PATH_LINKS_ENCLOSED_IN_AREA_BERLIN__10PCT));
			String line;
			while((line = linksReader.readLine()) != null){
				linksInArea.add(Id.create(line.split(",")[0], Link.class));
			}
			linksReader.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		DrtPtTripEventHandler eventHandler = new DrtPtTripEventHandler(scenario.getNetwork(), monitoredModes, null);//linksInArea);
		events.addHandler(eventHandler);
		new MatsimEventsReader(events).readFile(config.controler().getOutputDirectory() + "/output_events.xml.gz");
		System.out.println("Start writing trips of " + eventHandler.getPerson2ExperiencedTrips().size() + " agents.");
		ExperiencedTripsWriter tripsWriter = new ExperiencedTripsWriter(outputDirectory + "/experiencedTrips.csv", 
				eventHandler.getPerson2ExperiencedTrips(), monitoredModes);
		tripsWriter.writeExperiencedTrips();
		tripsWriter.closeWriter();
	}

}
