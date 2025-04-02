package org.geoserver.addamn.wps;

import org.geotools.coverage.util.IntersectUtils;
import org.geotools.api.data.FeatureSource;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.filter.text.ecql.ECQL;
import org.geotools.geometry.jts.JTS;
import org.geotools.process.factory.DescribeParameter;
import org.geotools.process.factory.DescribeProcess;
import org.geotools.process.factory.DescribeResult;
import org.geotools.referencing.CRS;
import org.geoserver.wps.WPSException;
import org.geoserver.wps.gs.GeoServerProcess;
import org.geoserver.wps.process.RawData;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.web.utils.ConfigLoader;
import org.geoserver.web.utils.GeoJson;
import org.geoserver.web.utils.LayerConfig;
import org.geoserver.web.utils.LayersConfig;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.locationtech.jts.geom.Geometry;
import org.geotools.api.feature.Feature;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.type.AttributeDescriptor;
import org.geotools.api.feature.type.FeatureType;
import org.geotools.api.feature.type.Name;
import org.geotools.api.filter.Filter;
import org.geotools.api.geometry.MismatchedDimensionException;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.api.referencing.NoSuchAuthorityCodeException;
import org.geotools.api.referencing.operation.TransformException;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

@DescribeProcess(title="addamnWPS", description="Application Dématérialisée des Demandes d'Autorisations en Milieux Naturels")
public class AddamnWPS implements GeoServerProcess {

	static final Logger logger = Logger.getLogger(AddamnWPS.class.getName());
	private Catalog catalog;

	public AddamnWPS(Catalog catalog) {
		this.catalog = catalog;
	}
	
	/**
	 * Return the same input GeoJSON with adding for each feature a organisme properties with the intersected features for each layer
	 * This method can be refactor for used the CQLFilter instead of intersect manualy
	 * @param geojson	The input GeoJSON
	 * @return			The GeoJSON as String
	 */
	public String getOrganisme(final RawData geojson) {
		// TODO : Refactor this code
		String response = null;
		try {
			LayersConfig layers = ConfigLoader.load();
			GeoJson geojsonUtil = new GeoJson();
			geojsonUtil.readGeoJson(geojson);
			CoordinateReferenceSystem geojsonCRS = geojsonUtil.getCRS();
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
					try {
						if(layer.getOrganismeColumn() != null && !layer.getOrganismeColumn().trim().isEmpty()) {
							FeatureCollection<? extends FeatureType, ? extends Feature> layerFeatures = getFeatureCollectionByLayerName(this.catalog, layer.getLayerName());
							try( FeatureIterator<? extends Feature> layerIt = layerFeatures.features() ) {
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
											String organisme = (String) layerFeature.getAttribute(layer.getOrganismeColumn()).toString();
											organismes.add(organisme);
										}
									}
								}
								newFeature.setAttribute(layer.getLayerName(), organismes); // Set the list of intersects organismes
							}
						} else {
							logger.log(Level.INFO, "Current layer : " + layer.getLayerName() + " don't have organisme column");
						}
					} catch (IOException e) {
						logger.log(Level.SEVERE, "Cannot get features layer source from layerName config", e);
						throw new WPSException("Cannot get features layer source from layerName config", e);
					} catch (FactoryException e) {
						logger.log(Level.SEVERE, "Error on create transformer proj from geojson to layer", e);
						throw new WPSException("Error on create transformer proj from geojson to layer", e);
					} catch (TransformException e) {
						logger.log(Level.SEVERE, "Error on reproject geojson geom to crs layer source", e);
						throw new WPSException("Error on reproject geojson geom to crs layer source", e);
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
	 * Create a CQL Filter based on geometry
	 * @param geojson		The input GeoJSON
	 * @param layerConfig	The layer config
	 * @param predicat		The predicat geometry
	 * @return				The String representation of CQL Filter
	 */
	public String getCQLFilterPolygon(final RawData geojson, LayerConfig layerConfig, String predicat) {
		GeoJson geojsonUtil = new GeoJson();
		CoordinateReferenceSystem geojsonCRS = null;
		try {
			geojsonUtil.readGeoJson(geojson);
			geojsonCRS = geojsonUtil.getCRS();
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Cannot create the CQL Filter String from geoJson", e);
			throw new WPSException("Cannot create the CQL Filter String from geoJson", e);
		} catch (FactoryException e) {
			logger.log(Level.SEVERE, "Cannot read CRS source of geojson", e);
			throw new WPSException("Cannot read CRS source of geojson", e);
		}
		String separator = "";
		StringBuilder sb = new StringBuilder();
		while(geojsonUtil.hasNext()){ // For each feature of geoJson
			SimpleFeature geojsonFeature = (SimpleFeature) geojsonUtil.next();
			Geometry geojsonGeom = (Geometry) geojsonFeature.getDefaultGeometry();
			sb.append(separator);
			sb.append("(" + predicat + "(");
			sb.append("\"" + layerConfig.getGeomColumnName() + "\",");
			sb.append("SRID=" + geojsonCRS.getIdentifiers().iterator().next().getCode());
			sb.append(";");
			sb.append(geojsonGeom.toText());
			sb.append("))");
			separator = " OR ";
		};
		try {
			//logger.log(Level.SEVERE, new String(sb.toString().getBytes(),"UTF-8"));
			return new String(sb.toString().getBytes(),"UTF-8");
		} catch (UnsupportedEncodingException e) {
			logger.log(Level.SEVERE, "Cannot parse to UTF-8", e);
			return sb.toString();
		}
	}

	/**
	 * Create a CQL Filter based on attributs
	 * @param layerConfig	The layer config
	 * @param arrayIds		List the id value
	 * @return				The String representation of CQL Filter
	 */
	public String getCQLFilterAttributs(LayerConfig layerConfig, JSONArray arrayIds) {
		/*String separator = "";
		StringBuilder sb = new StringBuilder();
		Iterator idsIt = arrayIds.iterator();
		sb.append("(");
		while(idsIt.hasNext()){ // For each feature of geoJson
			sb.append(separator);
			sb.append(layerConfig.getIdColumn());
			sb.append("=");
			sb.append("'" + idsIt.next() + "'");
			separator = " OR ";
		};
		sb.append(")");
		return sb.toString();*/
		String separator = "";
		StringBuilder sb = new StringBuilder();
		Iterator idsIt = arrayIds.iterator();
		sb.append(layerConfig.getIdColumn());
		sb.append(" IN (");
		while(idsIt.hasNext()){ // For each feature of geoJson
			sb.append(separator);
			sb.append("'" + idsIt.next() + "'");
			separator = ", ";
		};
		sb.append(")");
		return sb.toString();
		
	}

	/**
	 * Return a JSON object with the layername, the id's and organisme's who intersect the geojson  
	 * @param geojson		The input GeoJSON
	 * @param layersConfig	The layers config
	 * @param predicat		The predicat geometry (INTERSECTS/WITHIN)
	 * @return				A JSON string
	 */
	public String getIntersection(final RawData geojson, LayersConfig layersConfig, String predicat) {
		JSONArray jsonRoot = new JSONArray();
		String cqlFilter;
		Filter filter;
		for(LayerConfig layerConfig: layersConfig.getAll()) {
			cqlFilter = getCQLFilterPolygon(geojson, layerConfig, predicat);
			try {
				filter = ECQL.toFilter(cqlFilter);
			} catch (CQLException e) {
				logger.log(Level.SEVERE, "Cannot convert CQL filter String to Filter", e);
				throw new WPSException("Cannot convert CQL filter String to Filter", e);
			}
			JSONObject layerRoot = new JSONObject();
			layerRoot.put("name", layerConfig.getLayerName());
			layerRoot.put("label", layerConfig.getLayerTitle());
			FeatureCollection<? extends FeatureType, ? extends Feature> layerFeatures = null;
			try {
				layerFeatures = getFeatureCollectionByLayerName(this.catalog, layerConfig.getLayerName(), filter);
			} catch (IOException e) {
				logger.log(Level.SEVERE, "Cannot get Feature layer " + layerConfig.getLayerName() + " with filter : " + cqlFilter, e);
				throw new WPSException("Cannot get Feature layer " + layerConfig.getLayerName() + " with filter : " + cqlFilter, e);
			}
			try(FeatureIterator<? extends Feature> layerIt = layerFeatures.features()) {
				JSONArray jsOrgansimeArray = new JSONArray();
				JSONArray jsIdArray = new JSONArray();
				JSONArray jsLabelArray = new JSONArray();
				while(layerIt.hasNext()){ // For each feature of layer
					SimpleFeature layerFeature = (SimpleFeature) layerIt.next();
					if (layerConfig.getOrganismeColumn() != null && !layerConfig.getOrganismeColumn().trim().isEmpty() && featureHaveAttribute(layerFeature, layerConfig.getOrganismeColumn())) {
						jsOrgansimeArray.add(layerFeature.getAttribute(layerConfig.getOrganismeColumn()));
					}
					if (featureHaveAttribute(layerFeature, layerConfig.getIdColumn())) {
						jsIdArray.add(layerFeature.getAttribute(layerConfig.getIdColumn()));
					}
					if (featureHaveAttribute(layerFeature, layerConfig.getLabelColumn())) {
						jsLabelArray.add(layerFeature.getAttribute(layerConfig.getLabelColumn()));
					}
				}
				JSONObject paramCQL = new JSONObject();
				String cqlFilterAttributs = getCQLFilterAttributs(layerConfig, jsIdArray);
				paramCQL.put("CQL_FILTER", cqlFilterAttributs);
				//paramCQL.put("CQL_FILTER", cqlFilter);
				layerRoot.put("params", paramCQL);
				layerRoot.put("ids", jsIdArray);
				layerRoot.put("labels", jsLabelArray);
				layerRoot.put("organisme", jsOrgansimeArray);
				jsonRoot.add(layerRoot);
			}
		};
		return jsonRoot.toString();
	}

	private boolean isLayerValid(LayerConfig layer){
		if (this.catalog.getLayerByName(layer.getLayerName()) == null) {
			logger.log(Level.WARNING, "Layer '" + layer.getLayerName() + "' not found in GeoServer catalog. It will be removed from the config.");
			return false;
		}
		// Récupération du FeatureCollection
		FeatureCollection<? extends FeatureType, ? extends Feature> layerFeatures;
		try {
			layerFeatures = getFeatureCollectionByLayerName(this.catalog, layer.getLayerName());
		} catch (IOException e) {
			logger.log(Level.WARNING, "Layer '" + layer.getLayerName() + "' cannot getFeatureCollectionByLayerName. It will be removed from the config");
			return false;
		}
		try (FeatureIterator<? extends Feature> layerIt = layerFeatures.features()) {
			if (!layerIt.hasNext()) {
				logger.log(Level.WARNING, "Layer '" + layer.getLayerName() + "' has no features. It will be removed from the config.");
				return false;
			}
			SimpleFeature feature = (SimpleFeature) layerIt.next();
			// Vérifie que les colonnes id et label existent
			boolean hasId = featureHaveAttribute(feature, layer.getIdColumn());
			boolean hasLabel = featureHaveAttribute(feature, layer.getLabelColumn());
			if (!hasId || !hasLabel) {
				logger.log(Level.WARNING, "Layer '" + layer.getLayerName() + "' is missing required attributes (id: " + layer.getIdColumn() + ", label: " + layer.getLabelColumn() + "). It will be removed from the config.");
				return false;
			}
		}
		return true;
	}

	public LayersConfig getLayersList() {
		LayersConfig layers;
		try {
			layers = ConfigLoader.load();
			List<LayerConfig> validLayers = new ArrayList<>();
			for (LayerConfig layer : layers.getAll()) {
				if (this.isLayerValid(layer)) {
					validLayers.add(layer);
				} else {
					logger.log(Level.WARNING, "Layer '" + layer.getLayerName() + " is not a valid layer.");
				}
			}
			layers.getAll().clear();
			layers.getAll().addAll(validLayers);
			
			for (LayerConfig layer : layers.getAll()) {
				layer.setLayerTitle(this.catalog.getLayerByName(layer.getLayerName()).getTitle());
				try {
					FeatureCollection<? extends FeatureType, ? extends Feature> layerFeatures = getFeatureCollectionByLayerName(this.catalog, layer.getLayerName());
					try(FeatureIterator<? extends Feature> layerIt = layerFeatures.features()) {
						Name geomColumn = layerIt.next().getDefaultGeometryProperty().getName();
						layer.setGeomColumnName(geomColumn.getLocalPart());
					}
				} catch (IOException e) {
					logger.log(Level.SEVERE, "Cannot get the geom column Name for layer : " + layer.getLayerName(), e);
					throw new WPSException("Cannot get the geom column Name for layer : " + layer.getLayerName(), e);
				}
			};
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Cannot load config file", e);
			throw new WPSException("Cannot load config file", e);
		}
		return layers;
	}

	@DescribeResult(name="result", description="GeoJson output result with code organimse for each layer")
	public String execute(
			@DescribeParameter(name = "geometry", min = 0, max = 1, description = "Geojson input to intersect with referential layers and get organisme code",meta = { "mimeTypes=application/json" }) final RawData geojson,
			@DescribeParameter(name = "operation", min = 0, max = 1, defaultValue = "getOrganisme", description = "The operation name : getLayerList, getIntersection, getOrganisme") String WPSoperation,
			@DescribeParameter(name = "selectedLayers", min = 0, max = 1, defaultValue = "", description = "The selected layers for intersection, if not set, used all layers") String selectedLayers,
			@DescribeParameter(name = "predicat", min = 0, max = 1, defaultValue = "INTERSECTS", description = "The predicat type for the intersection feature (INTERSECT OR WITHIN), default INTERSECT") String predicat) {

		LayersConfig layers = getLayersList();
		if(! (selectedLayers.equals(null) || selectedLayers.equals("")) ){
			layers.filter(Arrays.asList(selectedLayers.split(",")));
		}
		switch (WPSoperation) {
			case "getLayerlist":
				String s = JSONArray.fromObject(layers.getAll()).toString();
				try {
					return new String(s.getBytes(),"UTF-8");
				} catch (UnsupportedEncodingException e) {
					logger.log(Level.SEVERE, "Cannot parse to UTF-8", e);
					return s;
				}
			case "getIntersection":
				return getIntersection(geojson, layers, predicat);
			case "getOrganisme":
				return getOrganisme(geojson);
			default: 
				return getOrganisme(geojson);
		}
	}

	/**
	 * Return the FeatureCollection for one Layer in GeoServer
	 * @param catalog	The GeoServer catalog
	 * @param layerName	The layerName
	 * @return	The FeatureCollection for this layer
	 * @throws IOException	If getFeatures failed
	 */
	public FeatureCollection<? extends FeatureType, ? extends Feature> getFeatureCollectionByLayerName(Catalog catalog, String layerName) throws IOException{
		return getFeatureCollectionByLayerName(catalog, layerName, null);
	}
	
	/**
	 * Return the FeatureCollection for one Layer in GeoServer
	 * @param catalog	The GeoServer catalog
	 * @param layerName	The layerName
	 * @param filter	A CQLFilter for filter the source layer
	 * @return	The FeatureCollection for this layer
	 * @throws IOException	If getFeatures failed
	 */
	public FeatureCollection<? extends FeatureType, ? extends Feature> getFeatureCollectionByLayerName(Catalog catalog, String layerName, Filter filter) throws IOException{
		FeatureTypeInfo info = catalog.getFeatureTypeByName(layerName);
		if (Objects.isNull(info)) {
			throw new WPSException(layerName + " : This layerName does not exist - Please update your config File");
		}
		FeatureSource<? extends FeatureType, ? extends Feature> fs = info.getFeatureSource(null, null);
		return fs.getFeatures(filter);
	}

	/**
	 * Check if a feature contains the attribute
	 * @param feature A feature
	 * @param attributeName The attributName to search
	 * @return True if attribute name is in feature
	 */
	public boolean featureHaveAttribute(SimpleFeature feature, String attributeName) {
		List<Boolean> res = new ArrayList<Boolean>();
		res.add(false);
		for(AttributeDescriptor attr: feature.getFeatureType().getAttributeDescriptors()) {
			if (attr.getLocalName().equalsIgnoreCase(attributeName)) {
				res.set(0, true);
				return true;
			}
		}
		return false;
	}
}