package org.geoserver.addamn.wps.config;

import java.util.ArrayList;
import java.util.List;

public class LayersConfig {
	/**
	 * This class represent the configFile as a list of LayerConfig
	 * Each LayerConfig is a config in config file
	 */
	private List<LayerConfig> layersList = new ArrayList<LayerConfig>();
	
	/**
	 * I
	 */
//	protected LayersConfig(){
//		this.layersList = new ArrayList<LayerConfig>();
//    }

	/**
	 * Add a LayerConfig to the list
	 * @param layer a LayerConfig
	 */
	protected void add(LayerConfig layer){
    	this.layersList.add(layer);
    }
    
	/**
	 * Return the list of LayerConfig
	 * @return a List
	 */
    public List<LayerConfig> getAll() {
    	// TODO : Implement an Iterator instead this ?
    	return this.layersList;
    }
}
