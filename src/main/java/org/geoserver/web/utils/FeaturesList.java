package org.geoserver.web.utils;

import java.util.ArrayList;
import java.util.List;

import org.geoserver.addamn.wps.config.LayerConfig;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;

public class FeaturesList {
	
	private List<SimpleFeature> features = new ArrayList<SimpleFeature>();
	private String layerName;
	private List<Geometry> geoJsonGeom = new ArrayList<Geometry>();
	
	public FeaturesList(String layerName) {
		this.layerName = layerName;
	}
	
	public void addFeature(SimpleFeature feature) {
		this.features.add(feature);
	}
	
	public String getLayerName() {
		return this.layerName;
	}
	
	public List<SimpleFeature> getFeatures(){
		return this.features;
	}
	
	/*public void setGeoJsonGeom(Geometry geom) {
		this.geoJsonGeom = geom;
	}*/
	public void addGeometry(Geometry geom) {
		geoJsonGeom.add(geom);
	}
	
	public List<Geometry> getGeoJsonGeom() {
		return this.geoJsonGeom;
	}
	

}
