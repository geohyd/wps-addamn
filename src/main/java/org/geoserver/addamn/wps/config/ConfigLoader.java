package org.geoserver.addamn.wps.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.geoserver.addamn.wps.AddamnWPS;
import org.geoserver.catalog.Catalog;
import org.geoserver.config.GeoServerDataDirectory;
import org.geoserver.platform.GeoServerExtensions;
import com.thoughtworks.xstream.XStream;
import org.geoserver.addamn.wps.config.LayersConfig;

public class ConfigLoader {
	/**
	 * Class for read the config file for this WPS
	 * If directory or config file doesn'y exist, it create a default structure and config example.
	 */

	static final Logger logger = Logger.getLogger(AddamnWPS.class.getName());

	/**
	 * Load a config File
	 * @return A LayersConfig represent the list of LayerConfig in the config file.
	 * @throws IOException If the initialisation failed
	 */
	public static LayersConfig load() throws IOException {
		File pf = getPersistenceFile(getGeoserverDataDirectory());
		if (!pf.exists()) {
			logger.log(Level.SEVERE, "Config file does not exist, create it");
			pf.createNewFile();
			initConfig(pf);
		}
		FileInputStream fis;
		fis = new FileInputStream(pf);
		XStream xstream = new XStream();
		xstream.alias("layer", LayerConfig.class);
		xstream.alias("layers", LayersConfig.class);
		xstream.addImplicitCollection(LayersConfig.class, "layersList");
		LayersConfig layers = (LayersConfig) xstream.fromXML(fis);
		fis.close();
		return layers;
	}

	/**
	 * Get the file from the geoserver DataDir
	 * If folder doesn't exist, it create it
	 * @param geoserverDD the File representation of DATADIR
	 * @return the config File path
	 */
	private static File getPersistenceFile(File geoserverDD) {
		String strCfgFile = geoserverDD.toString() + "/wps_addamn";
		File cfgFile = new File(strCfgFile);
		if (!cfgFile.exists()) {
			cfgFile.mkdir();
		}
		strCfgFile += "/config.xml";
		cfgFile = new File(strCfgFile);
		return cfgFile;
	}

	/**
	 * Create an mockup config file.
	 * @param persistenceFile	The path a config File
	 * @throws IOException	If CRUD file failed
	 */
	private static void initConfig(File persistenceFile) throws IOException {
		FileWriter fw = new FileWriter(persistenceFile, false);
		XStream xstream = new XStream();
		xstream.alias("layer", LayerConfig.class);
		xstream.alias("layers", LayersConfig.class);
		xstream.addImplicitCollection(LayersConfig.class, "layersList");
		LayersConfig list = new LayersConfig();
		list.add(new LayerConfig("layerName","organismeColumn","idColumn"));
		list.add(new LayerConfig("layerName","organismeColumn","idColumn"));
		String xml = xstream.toXML(list);
		fw.write(xml);
		fw.flush();
		fw.close();
	}

	/**
	 * Get the geoserver DataDir File from the geoserver catalog
	 * @return The DataDir File
	 */
	private static File getGeoserverDataDirectory() {
		// TODO: how to get the Geoserverdatadriectory properly? Seems to be changed for Geoserver 2.6.x or earlier
		// return GeoserverDataDirectory.getGeoserverDataDirectory();
		Catalog cat = (Catalog) GeoServerExtensions.bean("catalog");
		GeoServerDataDirectory dd = new GeoServerDataDirectory(cat.getResourceLoader());
		return dd.root();
	}

}
