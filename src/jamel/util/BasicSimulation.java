package jamel.util;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

import org.jfree.data.xy.XYSeries;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import jamel.Jamel;
import jamel.data.Export;
import jamel.data.Expression;
import jamel.data.ExpressionFactory;
import jamel.gui.DynamicXYSeries;
import jamel.gui.Gui;
import jamel.gui.Updatable;

/**
 * A basic simulation.
 */
public class BasicSimulation implements Simulation {

	/**
	 * Creates and returns a new Gui.
	 * 
	 * @param elem
	 *            an XML element that contains the description of the Gui.
	 * @param simulation
	 *            the parent simulation.
	 * @return a new Gui.
	 */
	private static Gui getNewGui(final Element elem, final Simulation simulation) {

		if (!elem.getNodeName().equals("gui")) {
			throw new RuntimeException("Bad element: " + elem.getNodeName());
		}

		final File guiFile;
		final Element guiDescription;

		if (elem.hasAttribute("src")) {

			/*
			 * Opens and reads the XML file that contains the specification of the gui.
			 */

			final String src = elem.getAttribute("src");
			final String fileName = simulation.getFile().getParent() + "/" + src;
			guiFile = new File(fileName);
			final Element root;
			try {
				final Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(guiFile);
				root = document.getDocumentElement();
			} catch (final Exception e) {
				throw new RuntimeException("Something went wrong while reading \"" + fileName + "\"", e);
			}
			if (!root.getTagName().equals("gui")) {
				throw new RuntimeException(fileName + ": Bad element: " + root.getTagName());
			}
			guiDescription = root;

		} else {
			guiFile = simulation.getFile();
			guiDescription = elem;
		}

		final String guiClassName = guiDescription.getAttribute("className");
		if (guiClassName.isEmpty()) {
			throw new RuntimeException("Attribute \"className\" is missing or empty.");
		}

		/*
		 *  Creates the gui.
		 */

		final Gui gui;
		try {
			gui = (Gui) Class.forName(guiClassName, false, ClassLoader.getSystemClassLoader())
					.getConstructor(Element.class, File.class, Simulation.class)
					.newInstance(guiDescription, guiFile, simulation);
		} catch (Exception e) {
			throw new RuntimeException("Something went wrong while creating the gui.", e);
		}

		return gui;
	}

	/**
	 * Creates and returns a new sector.
	 * 
	 * @param specification
	 *            an XML element that specifies the sector to be created.
	 * @param simulation
	 *            the parent simulation
	 * 
	 * @return the new sector.
	 */
	private static Sector getNewSector(Element specification, Simulation simulation) {
		if (!specification.getNodeName().equals("sector")) {
			throw new RuntimeException("Bad element: " + specification.getNodeName());
		}

		final String sectorClassName = specification.getAttribute("className");
		if (sectorClassName.isEmpty()) {
			throw new RuntimeException("Attribute \"className\" is missing or empty.");
		}

		final Sector sector;
		try {
			sector = (Sector) Class.forName(sectorClassName, false, ClassLoader.getSystemClassLoader())
					.getConstructor(Element.class, Simulation.class).newInstance(specification, simulation);
		} catch (Exception e) {
			throw new RuntimeException("Something went wrong while creating the sector.", e);
		}

		return sector;
	}

	/** The events. */
	private final Map<Integer, Element> events = new HashMap<>();

	/**
	 * The list of the exports.
	 */
	final private List<Export> exports = new LinkedList<>();

	/**
	 * The expression factory;
	 */
	final private ExpressionFactory expressionFactory = new ExpressionFactory(this);

	/**
	 * The file that contains the description of the simulation.
	 */
	final private File file;

	/**
	 * The Gui.
	 */
	final private Gui gui;

	/**
	 * The name of the simulation.
	 */
	private final String name;

	/**
	 * A flag that indicates whether the simulation is paused or not.
	 */
	private boolean pause = false;

	/**
	 * The list of the phases of the period.
	 */
	final private List<Phase> phases = new LinkedList<>();

	/**
	 * The random.
	 */
	final private Random random;

	/**
	 * A flag that indicates if the simulation runs.
	 */
	private boolean run = false;

	/**
	 * An XML element that contains the description of the simulation.
	 */
	final private Element scenario;

	/**
	 * The collection of the sectors, with access by their names.
	 */
	final private Map<String, Sector> sectors = new HashMap<>();

	/**
	 * The timer.
	 */
	final private Timer timer;

	/**
	 * The list of the series to update.
	 */
	private List<Updatable> updatableSeries = new LinkedList<>();

	/**
	 * Creates an new simulation.
	 * 
	 * @param scenario
	 *            An XML element that contains the description of the
	 *            simulation.
	 * @param file
	 *            The file that contains the description of the simulation.
	 */
	public BasicSimulation(final Element scenario, final File file) {

		this.scenario = scenario;
		this.file = file;
		this.timer = new BasicTimer(0);
		this.name = scenario.getAttribute("name");

		// Inits the random.

		{
			final NodeList nodeList = this.scenario.getElementsByTagName("randomSeed");
			if (nodeList.getLength() == 0) {
				throw new RuntimeException("Missing tag : randomSeed");
			}
			final int randomSeed = Integer.parseInt(nodeList.item(0).getTextContent().trim());
			this.random = new Random(randomSeed);
		}

		// Looks for the sectors.

		{
			final Element sectorsTag = (Element) this.scenario.getElementsByTagName("sectors").item(0);
			if (sectorsTag == null) {
				throw new RuntimeException("Missing tag : sectors");
			}
			final NodeList nodeList = sectorsTag.getElementsByTagName("sector");
			for (int index = 0; index < nodeList.getLength(); index++) {
				final Sector sector = getNewSector((Element) nodeList.item(index), this);
				this.sectors.put(sector.getName(), sector);
				Jamel.println("new sector", sector.getName());
			}
		}

		// Looks for the phases.

		{
			final Element phasesTag = (Element) this.scenario.getElementsByTagName("phases").item(0);
			if (phasesTag == null) {
				throw new RuntimeException("Missing tag : phasesTag");
			}
			final NodeList phaseList = phasesTag.getElementsByTagName("phase");
			for (int i = 0; i < phaseList.getLength(); i++) {
				final Element phaseTag = (Element) phaseList.item(i);
				final String phaseName = phaseTag.getAttribute("name");
				final NodeList sectorList = phaseTag.getElementsByTagName("sector");
				for (int j = 0; j < sectorList.getLength(); j++) {
					final String sectorName = sectorList.item(j).getTextContent().trim();
					final Sector sector = this.sectors.get(sectorName);
					if (sector == null) {
						throw new RuntimeException("Sector not found: " + sectorName);
					}
					final Phase phase = sector.getPhase(phaseName);
					if (phase == null) {
						throw new RuntimeException(
								"Sector: " + sectorName + ", unable to create the phase: " + phaseName);
					}
					this.phases.add(phase);
				}
			}
		}

		// Looks for the exports.

		{
			final Element exportsTag = (Element) this.scenario.getElementsByTagName("exports").item(0);
			if (exportsTag != null) {
				final NodeList exportList = exportsTag.getElementsByTagName("export");
				for (int i = 0; i < exportList.getLength(); i++) {
					this.exports.add(new Export((Element) exportList.item(i), this));
				}
			}
		}

		// Looks for the events.

		{
			final Element eventsTag = (Element) this.scenario.getElementsByTagName("events").item(0);
			if (eventsTag != null) {
				final NodeList eventList = eventsTag.getElementsByTagName("event");
				for (int i = 0; i < eventList.getLength(); i++) {
					final Element element = (Element) eventList.item(i);
					final int period = Integer.parseInt(element.getAttribute("when"));
					if (this.events.containsKey(period)) {
						throw new RuntimeException("Events already defined for the period: " + period);
					}
					this.events.put(period, element);
				}
			}
		}

		// Looks for the gui.

		{
			final NodeList nodeList = this.scenario.getElementsByTagName("gui");
			if (nodeList.getLength() == 0) {
				this.gui = null;
			} else {
				this.gui = getNewGui((Element) nodeList.item(0), this);
			}
		}

	}

	/**
	 * Executes the specified event.
	 * 
	 * @param event
	 *            the event to be executed.
	 */
	private void doEvent(Element event) {
		switch (event.getTagName()) {
		case "pause":
			this.pause = true;
			break;
		default:
			throw new RuntimeException("Not yet implemented: \'" + event.getTagName() + "\'");
		}
	}

	/**
	 * Executes the events of the simulation.
	 */
	private void doEvents() {
		final Element currentEvents = this.events.get(getPeriod());
		if (currentEvents != null) {
			final NodeList eventList = currentEvents.getChildNodes();
			for (int i = 0; i < eventList.getLength(); i++) {
				if (eventList.item(i).getNodeType() == Node.ELEMENT_NODE) {
					final Element event = (Element) eventList.item(i);
					if (event.getAttribute("recipient").isEmpty()) {
						this.doEvent(event);
					} else if (event.getAttribute("recipient").equals("gui")) {
						this.gui.doEvent(event);
					} else {
						final String sectorName = event.getAttribute("recipient");
						this.sectors.get(sectorName).doEvent(event);
					}
				}
			}
		}
	}

	/**
	 * Pauses the simulation.
	 */
	private void doPause() {
		if (isPaused()) {
			// TODO clean up
			// final long startPause = new Date().getTime();
			this.gui.update();
			while (isPaused()) {
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			this.gui.update();
			// final long endPause = new Date().getTime();
			// this.pausedTime += endPause - startPause;
			// this.gui.repaintControls(); TODO ??
		}
	}

	/**
	 * Executes a period of the simulation.
	 */
	private void period() {
		for (final Phase phase : this.phases) {
			try {
				phase.run();
			} catch (Exception e) {
				throw new RuntimeException("Something went wrong while running the phase: '" + phase.getName()
						+ "', for the sector: '" + phase.getSector().getName() + "'.", e);
			}
		}
		for (final Updatable updatable : this.updatableSeries) {
			updatable.update();
		}
		if (gui != null) {
			this.gui.update();
		}
		for (final Export export : this.exports) {
			export.run();
		}
		this.doEvents();
		this.doPause();
		this.timer.next();
		Jamel.println("period", this.timer.getPeriod());
	}

	@Override
	public Expression getDataAccess(final String key) {
		final Expression result;
		if (key.equals("period")) {
			result = new Expression() {

				@Override
				public Double getValue() {
					return (double) timer.getPeriod();
				}

				@Override
				public String toString() {
					return "period";
				}

			};
		} else if (Pattern.matches("value[\\(].*[\\)]", key)) {
			final String argString = key.substring(6, key.length() - 1);
			final String[] split = argString.split(",");
			final Sector sector = this.sectors.get(split[0]);
			if (sector == null) {
				throw new RuntimeException("Sector not found: " + split[0]);
			}
			final String[] args = Arrays.copyOfRange(split, 1, split.length);
			// TODO le premier argument devrait contenir non seulement le nom du
			// secteur, mais aussi (éventuellement) des instructions permettant
			// de limiter la sélection à un sous ensemble des agents.
			result = sector.getDataAccess(args);
		} else {
			throw new RuntimeException("Not yet implemented: \'" + key + "\'");
		}
		return result;
	}

	@Override
	public Expression getExpression(String key) {
		return this.expressionFactory.getExpression(key);
	}

	@Override
	public File getFile() {
		return this.file;
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public int getPeriod() {
		return this.timer.getPeriod();
	}

	/**
	 * Returns the random.
	 * 
	 * @return the random.
	 */
	@Override
	public Random getRandom() {
		return random;
	}

	@Override
	public XYSeries getSeries(String x, String y) {
		final Expression xExp = getExpression(x);
		final Expression yExp = getExpression(y);
		final DynamicXYSeries newSeries = new DynamicXYSeries(xExp, yExp);
		this.updatableSeries.add(newSeries);
		return newSeries;
	}

	@Override
	public boolean isPaused() {
		return this.pause;
	}

	@Override
	public void pause() {
		this.pause = !this.pause;
	}

	@Override
	public void run() {
		this.run = true;
		while (this.run) {
			this.period();
		}
	}

}
