package jamel.basic.sector;

import jamel.basic.data.SectorDataset;
import jamel.basic.util.InitializationException;

import org.w3c.dom.Element;

/**
 * The interface for the sector components of the macro-economic circuit. 
 */
public interface Sector {
	
	/**
	 * Executes the specified event.
	 * @param event a XML element that describes the event to be executed.
	 */
	void doEvent(Element event);

	/**
	 * Returns the sector dataset.
	 * @return the sector dataset.
	 */
	SectorDataset getDataset();

	/**
	 * Returns the name of the sector.
	 * @return the name of the sector.
	 */
	String getName();

	/**
	 * Returns the specified phase.
	 * @param name the name of the phase.
	 * @return a phase.
	 */
	Phase getPhase(String name);

	/**
	 * Initializes the sector.
	 * Must be called once each sector is already created.
	 * @param element an XML element that contains the description of the parameters of the sector.
	 * @throws InitializationException if something goes wrong.
	 */
	void init(Element element) throws InitializationException;
	
}

// ***
