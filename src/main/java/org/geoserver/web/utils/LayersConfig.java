package org.geoserver.web.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class LayersConfig {
	/**
	 * This class represent the configFile as a list of LayerConfig
	 * Each LayerConfig is a config in config file
	 */
	private List<LayerConfig> layersList = new ArrayList<LayerConfig>();
	
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
    
    public LayerConfig getLayer(String layerName) {
    	return layersList.stream()
    			.filter(layer -> layer.getLayerName().equalsIgnoreCase(layerName))
    			.findFirst()
    			.orElse(null);
    }
    
    public String getTitleLayer(String layerName) {
    	LayerConfig layerConfig = layersList.stream()
			.filter(layer -> layer.getLayerName().equalsIgnoreCase(layerName))
			.findFirst()
			.orElse(null);
    	if (Objects.isNull(layerConfig) ){  
    		return "";
    	}else {
    		return layerConfig.getLayerTitle();
    	}
    }
    
    public void filter(List<String> layersFilters) {
    	this.layersList = 
    			layersList.stream()
    					.filter(layer -> layersFilters.contains(layer.getLayerName()))
    		           .collect(Collectors.toList());
    }
}
