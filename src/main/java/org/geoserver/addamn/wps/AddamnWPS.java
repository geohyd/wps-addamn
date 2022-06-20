package org.geoserver.addamn.wps;

import org.geotools.coverage.util.IntersectUtils;
import org.geotools.data.FeatureSource;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.geometry.jts.JTS;
import org.geotools.process.factory.DescribeParameter;
import org.geotools.process.factory.DescribeProcess;
import org.geotools.process.factory.DescribeResult;
import org.geotools.referencing.CRS;
import org.geoserver.wps.WPSException;
import org.geoserver.wps.gs.GeoServerProcess;
import org.geoserver.wps.process.RawData;
import org.geoserver.addamn.wps.config.ConfigLoader;
import org.geoserver.addamn.wps.config.LayersConfig;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.web.utils.GeoJson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.type.FeatureType;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;


@DescribeProcess(title="addamnWPS", description="Application Dématérialisée des Demandes d'Autorisations en Milieux Naturels")
public class AddamnWPS implements GeoServerProcess {

	static final Logger logger = Logger.getLogger(AddamnWPS.class.getName());
	private Catalog catalog;

	public AddamnWPS(Catalog catalog) {
		this.catalog = catalog;
	}

	@DescribeResult(name="result", description="GeoJson output result with code organimse for each layer")
	public String execute(@DescribeParameter(name = "geojson", description = "Geojson input to intersect with referential layers and get organisme code",meta = { "mimeTypes=application/json" }) final RawData geojson) {
		String response = null;
		try {
			LayersConfig layers = ConfigLoader.load();
			GeoJson geojsonUtil = new GeoJson();
			geojsonUtil.readGeoJson(geojson);
			CoordinateReferenceSystem geojsonCRS = geojsonUtil.getCRS();
			// TODO : This 3 line can be write in 1 with lambda
			List<String> newAttr = new ArrayList<String>();
			layers.getAll().forEach(layer -> newAttr.add(layer.getLayerName()));
			geojsonUtil.createFeatureBuilder(newAttr);
			while(geojsonUtil.hasNext()){ // For each feature of geoJson
				SimpleFeature geojsonFeature = (SimpleFeature) geojsonUtil.next();
				// For each Feature in geojson Source, we create a new Feature with the geoJson Class
				// By default, a Feature is immutable.
				// Each new Feature hav the intiale properties plus the newAttr call in createFeatureBuilder
				SimpleFeature newFeature = geojsonUtil.newFeature(geojsonFeature);
				layers.getAll().forEach(layer -> { // For each Layer in configuration file
					FeatureIterator<? extends Feature> layerIt = null;
					try {
						FeatureCollection<? extends FeatureType, ? extends Feature> layerFeatures = getFeatureCollectionByLayerName(this.catalog, layer.getLayerName());
						layerIt = layerFeatures.features();
						CoordinateReferenceSystem layerCRS = layerFeatures.getSchema().getCoordinateReferenceSystem();
						MathTransform transform = CRS.findMathTransform(geojsonCRS, layerCRS);
						Geometry geojsonGeom = (Geometry) geojsonFeature.getDefaultGeometry();
						// TODO : Reproject only if the CRS are not the same !
						Geometry geojsonGeomReproj = JTS.transform(geojsonGeom, transform);
						List<String> organismes = new ArrayList<String>(); // Contains the list of organismes who intersects
						while(layerIt.hasNext()){ // For each feature of layer
							// TODO : for a better perf, I think this loop need to be the root loop
							SimpleFeature layerFeature = (SimpleFeature) layerIt.next();
							Geometry layerGeom = (Geometry) layerFeature.getDefaultGeometry();
							if (geojsonGeom != null && geojsonGeom.isValid() && layerGeom != null && layerGeom.isValid()) {
								if( IntersectUtils.intersects(geojsonGeomReproj, layerGeom) ) {
									if (!featureHaveAttribute(layerFeature, layer.getOrganismeColumn())) {
										throw new WPSException("Layer " + layer.getLayerName() 
										+ " does not have attribute " 
										+ layer.getOrganismeColumn() 
										+ ". Please update your config File");
									}
									String organisme = (String) layerFeature.getAttribute(layer.getOrganismeColumn());
									organismes.add(organisme);
								}
							}
						}
						newFeature.setAttribute(layer.getLayerName(), organismes); // Set the list of intersects organismes
					} catch (IOException e) {
						logger.log(Level.SEVERE, "Cannot get features layer source from layerName config", e);
						throw new WPSException("Cannot get features layer source from layerName config", e);
					} catch (FactoryException e) {
						logger.log(Level.SEVERE, "Error on create transformer proj from geojson to layer", e);
						throw new WPSException("Error on create transformer proj from geojson to layer", e);
					} catch (TransformException e) {
						logger.log(Level.SEVERE, "Error on reproject geojson geom to crs layer source", e);
						throw new WPSException("Error on reproject geojson geom to crs layer source", e);
					} finally {
						if (!Objects.isNull(layerIt)) {
							layerIt.close();
						}
					}
				});
			}
			// Read the final geojson
			response = geojsonUtil.toGeoJson();
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Cannot load config file or read geojson file", e);
			throw new WPSException("Cannot load config file or read geojson file", e);
		} catch (FactoryException e) {
			logger.log(Level.SEVERE, "Cannot read CRS source of geojson", e);
			throw new WPSException("Cannot read CRS source of geojson", e);
		}
		if (response == null ) {
			throw new WPSException("Cannot compute process ; response is null");
		}
		return response;
	}

	/**
	 * Return the FeatureCollection for one Layer in GeoServer
	 * @param catalog	The GeoServer catalog
	 * @param layerName	The layerName
	 * @return	The FeatureCollection for this layer
	 * @throws IOException	If getFeatures failed
	 */
	public FeatureCollection<? extends FeatureType, ? extends Feature> getFeatureCollectionByLayerName(Catalog catalog, String layerName) throws IOException{
		FeatureTypeInfo info = catalog.getFeatureTypeByName(layerName);
		if (Objects.isNull(info)) {
			throw new WPSException(layerName + " : This layerName does not exist - Please update your config File");
		}
		FeatureSource<? extends FeatureType, ? extends Feature> fs = info.getFeatureSource(null, null);
		return fs.getFeatures();
	}
	
	/**
	 * Check if a feature contains the attribute
	 * @param feature A feature
	 * @param attributeName The attributName to search
	 * @return True if attribute name is in feature
	 */
	public boolean featureHaveAttribute(SimpleFeature feature, String attributeName) {
		// TODO : Why I have a 
		// Local variable name defined in an enclosing scope must be final or effectively final
		// This solution is not very pretty
		List<Boolean> res = new ArrayList<Boolean>();
		res.add(false);
		feature.getFeatureType().getAttributeDescriptors().forEach(attr -> {
			if (attr.getLocalName().equalsIgnoreCase(attributeName)) {
				res.set(0, true);
			}
		});
		return res.get(0);
	}
}