package org.matsim.integration.daily.accessibility;

import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.accessibility.AccessibilityConfigGroup;
import org.matsim.contrib.accessibility.Modes4Accessibility;
import org.matsim.contrib.accessibility.utils.AccessibilityRunUtils;
import org.matsim.contrib.matrixbasedptrouter.MatrixBasedPtRouterConfigGroup;
import org.matsim.contrib.matrixbasedptrouter.utils.BoundingBox;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlansConfigGroup;
import org.matsim.core.config.groups.StrategyConfigGroup.StrategySettings;
import org.matsim.core.config.groups.VspExperimentalConfigGroup.VspDefaultsCheckingLevel;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.testcases.MatsimTestUtils;

public class AccessibilityComputationKiberaTest {
	public static final Logger log = Logger.getLogger( AccessibilityComputationKiberaTest.class ) ;

//	private static final double cellSize = 25.;
	private static final Double cellSize = 100.;

	@Rule public MatsimTestUtils utils = new MatsimTestUtils() ;


	@Test
	public void doAccessibilityTest() throws IOException {
		// Input and output
		String folderStructure = "../../../"; // local on dz's computer
//		String folderStructure = "../../"; // server
		
		String networkFile = "matsimExamples/countries/ke/kibera/2015-11-05_network_paths_detailed.xml";

		folderStructure = PathUtils.tryANumberOfFolderStructures(folderStructure, networkFile);

		networkFile = folderStructure + networkFile ;	
		String facilitiesFile = folderStructure + "matsimExamples/countries/ke/kibera/2015-11-05_facilities.xml";
		
		// no pt input
		
		// Parameters
		boolean createQGisOutput = false;
		boolean includeDensityLayer = false;
		String crs = "EPSG:21037"; // = Arc 1960 / UTM zone 37S, for Nairobi, Kenya
		String name = "ke_kibera_" + cellSize.toString().split("\\.")[0];
		
		Double lowerBound = 2.;
		Double upperBound = 5.5;
		Integer range = 9; // in the current implementation, this need always be 9
		int symbolSize = 110;
		int populationThreshold = (int) (200 / (1000/cellSize * 1000/cellSize));

		
		// config and scenario
		Config config = ConfigUtils.createConfig(new AccessibilityConfigGroup(), new MatrixBasedPtRouterConfigGroup());
		config.network().setInputFile(networkFile);
		config.facilities().setInputFile(facilitiesFile);
		config.controler().setOverwriteFileSetting(OverwriteFileSetting.deleteDirectoryIfExists);
		config.controler().setOutputDirectory(utils.getOutputDirectory());
		config.controler().setLastIteration(0);

		config.vspExperimental().setVspDefaultsCheckingLevel(VspDefaultsCheckingLevel.abort);

		// some (otherwise irrelevant) settings to make the vsp check happy:
		config.timeAllocationMutator().setMutationRange(7200.);
		config.timeAllocationMutator().setAffectingDuration(false);
		config.plans().setRemovingUnneccessaryPlanAttributes(true);
		config.plans().setActivityDurationInterpretation( PlansConfigGroup.ActivityDurationInterpretation.tryEndTimeThenDuration );

		{
			StrategySettings stratSets = new StrategySettings(ConfigUtils.createAvailableStrategyId(config));
			stratSets.setStrategyName(DefaultPlanStrategiesModule.DefaultSelector.ChangeExpBeta.toString());
			stratSets.setWeight(1.);
			config.strategy().addStrategySettings(stratSets);
		}
		
		Scenario scenario = ScenarioUtils.loadScenario(config);
		
		
		BoundingBox boundingBox = BoundingBox.createBoundingBox(scenario.getNetwork());
		double xMin = boundingBox.getXMin();
		double xMax = boundingBox.getXMax();
		double yMin = boundingBox.getYMin();
		double yMax = boundingBox.getYMax();
		double[] mapViewExtent = {xMin, yMin, xMax, yMax};

		
		// no pt block
		
		
		assertNotNull(config);
		

		// collect activity types
		List<String> activityTypes = AccessibilityRunUtils.collectAllFacilityTypes(scenario);
		log.warn( "found activity types: " + activityTypes );
		// yyyy there is some problem with activity types: in some algorithms, only the first letter is interpreted, in some
		// other algorithms, the whole string.  BEWARE!  This is not good software design and should be changed.  kai, feb'14

		// collect homes
//		String activityFacilityType = "h";
//		ActivityFacilities homes = AccessibilityRunUtils.collectActivityFacilitiesOfType(scenario, activityFacilityType);


		Map<String, ActivityFacilities> activityFacilitiesMap = new HashMap<String, ActivityFacilities>();
		
		Controler controler = new Controler(scenario) ;
		controler.addOverridingModule(new AccessibilityComputationTestModule(activityTypes, null, crs, name, cellSize));
		controler.run();


		if (createQGisOutput == true) {
			String workingDirectory =  config.controler().getOutputDirectory();
			String osName = System.getProperty("os.name");

			for (String actType : activityTypes) {
				String actSpecificWorkingDirectory = workingDirectory + actType + "/";

				for ( Modes4Accessibility mode : Modes4Accessibility.values()) {
					if ( !actType.equals("drinking_water") ) {
						log.error("skipping everything except work for debugging purposes; remove in production code. kai, feb'14") ;
						continue ;
					}
					VisualizationUtils.createQGisOutput(actType, mode, mapViewExtent, workingDirectory, crs, includeDensityLayer,
							lowerBound, upperBound, range, symbolSize, populationThreshold);
					VisualizationUtils.createSnapshot(actSpecificWorkingDirectory, mode, osName);
				}
			}  
		}
	}
}