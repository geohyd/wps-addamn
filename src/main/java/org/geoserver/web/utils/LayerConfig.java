package org.geoserver.web.utils;


public class LayerConfig {
	/**
	 * This class represent one LayerConfig in the config file
	 */
	private String layers;
	private String layerName;
	private String layerTitle = "";
	private String organismeColumn;
	private String idColumn;
	private String labelColumn;
	private String geomColumnName;
	//typeNames
	
	/**
	 * Create a new LayerConfig
	 * @param layerName	The name of Layer (same as name of layer in geoserver)
	 * @param organismeColumn	The organisme column name for this layer
	 */
	protected LayerConfig(String name, String organismeColumn, String idColumn, String labelColumn) {
		this.layerName = layerName;
		this.organismeColumn = organismeColumn;
		this.idColumn = idColumn;
		this.labelColumn= labelColumn; 
	}
	
	/**
	 * Return the layerName
	 * @return String layerName
	 */
	public String getLayerName() {
		return this.layerName;
	}

	
	
	/**
	 * Return the layerName
	 * @return String layerName
	 */
	public void setLayerTitle(String title) {
		this.layerTitle = title;
	}
	/**
	 * Return the layerName
	 * @return String layerName
	 */
	public String getLayerTitle() {
		return this.layerTitle;
	}
	
	public String getGeomColumnName() {
		return this.geomColumnName;
	}
	
	public void setGeomColumnName(String name) {
		this.geomColumnName = name;
	}
	
	/**
	 * Return the Organisme column name
	 * @return String organismeColumn
	 */
	public String getOrganismeColumn() {
		return this.organismeColumn;
	}
	
	/**
	 * Return the id column name
	 * @return String idColumn
	 */
	public String getIdColumn() {
		return this.idColumn;
	}
	
	/**
	 * Return the label column name
	 * @return String labelColumn
	 */
	public String getLabelColumn() {
		return this.labelColumn;
	}
}
