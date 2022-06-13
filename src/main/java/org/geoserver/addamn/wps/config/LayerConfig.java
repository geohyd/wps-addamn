package org.geoserver.addamn.wps.config;


public class LayerConfig {
	/**
	 * This class represent one LayerConfig in the config file
	 */
	private String layerName;
	private String organismeColumn;
	
	/**
	 * Create a new LayerConfig
	 * @param layerName	The name of Layer (same as name of layer in geoserver)
	 * @param organismeColumn	The organisme column name for this layer
	 */
	protected LayerConfig(String layerName, String organismeColumn) {
		this.layerName = layerName;
		this.organismeColumn = organismeColumn;
	}
	
	/**
	 * Return the layerName
	 * @return String layerName
	 */
	public String getLayerName() {
		return this.layerName;
	}
	
	/**
	 * Return the Organisme column name
	 * @return String organismeColumn
	 */
	public String getOrganismeColumn() {
		return this.organismeColumn;
	}
}
