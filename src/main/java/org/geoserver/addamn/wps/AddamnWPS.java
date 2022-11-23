package org.geoserver.addamn.wps;

import org.geotools.coverage.util.IntersectUtils;
import org.geotools.data.FeatureSource;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.filter.text.cql2.CQL;
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.filter.text.ecql.ECQL;
import org.geotools.geojson.geom.GeometryJSON;
import org.geotools.geometry.jts.JTS;
import org.geotools.process.factory.DescribeParameter;
import org.geotools.process.factory.DescribeProcess;
import org.geotools.process.factory.DescribeResult;
import org.geotools.referencing.CRS;
import org.geoserver.wps.WPSException;
import org.geoserver.wps.gs.GeoServerProcess;
import org.geoserver.wps.process.RawData;
import org.geoserver.addamn.wps.config.ConfigLoader;
import org.geoserver.addamn.wps.config.LayerConfig;
import org.geoserver.addamn.wps.config.LayersConfig;
import org.geoserver.catalog.AttributeTypeInfo;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.FeatureTypeInfo;
//import org.geoserver.util.Filter;
import org.geoserver.web.utils.FeaturesList;
import org.geoserver.web.utils.GeoJson;
import org.geoserver.web.utils.GeoJsonLayer;
import org.geoserver.web.utils.GeoJsonString;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.MultiPolygon;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.feature.type.Name;
import org.opengis.filter.Filter;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

@DescribeProcess(title="addamnWPS", description="Application Dématérialisée des Demandes d'Autorisations en Milieux Naturels")
public class AddamnWPS implements GeoServerProcess {
	
	//TODO : Remove web.utils. ... not used class

	static final Logger logger = Logger.getLogger(AddamnWPS.class.getName());
	private Catalog catalog;

	public AddamnWPS(Catalog catalog) {
		this.catalog = catalog;
	}

	
	public String getOrganisme(final RawData geojson) {
		// TODO : Refactor this code
		String response = null;
		try {
			//List<String, String> geojsonArray = new ArrayList<String, String>();
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
					try {
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
										String organisme = (String) layerFeature.getAttribute(layer.getOrganismeColumn());
										organismes.add(organisme);
									}
								}
							}
							newFeature.setAttribute(layer.getLayerName(), organismes); // Set the list of intersects organismes
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

	public String getCQLFilter(FeaturesList layer, LayerConfig layerConfig) {
		StringBuilder sb = new StringBuilder();
		String idColumn = layerConfig.getIdColumn();
		sb.append(idColumn);
		sb.append(" IN ('");
		List<String> ids = new ArrayList<String>();
		layer.getFeatures().forEach(feature -> {
			//TODO : unique list !
			String idValue = (String) feature.getAttribute(idColumn);
			ids.add(idValue);
		});
		sb.append(String.join("', '", ids));
		sb.append("')");
		return sb.toString();
	}

	
	public String getCQLFilterPolygon2(final RawData geojson, LayerConfig layerConfig) {
		List<String> inter = new ArrayList<String>();
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
			//StringBuilder sb = new StringBuilder();
			sb.append(separator);
			sb.append("(INTERSECTS(");
			sb.append("\"" + layerConfig.getGeomColumnName() + "\",");
			sb.append("SRID=" + geojsonCRS.getIdentifiers().iterator().next().getCode());
			sb.append(";");
			sb.append(geojsonGeom.toText());
			sb.append("))");
			//inter.add(sb.toString());
			separator = " OR ";
		};
		//return String.join(" OR ", inter);
		try {
			return new String(sb.toString().getBytes(),"UTF-8");
		} catch (UnsupportedEncodingException e) {
			logger.log(Level.SEVERE, "Cannot parse to UTF-8", e);
			return sb.toString();
		}
		//return sb.toString();
	}
	
	public String getCQLFilterAttributs(LayerConfig layersConfig, JSONArray arrayIds) {
		List<String> inter = new ArrayList<String>();
		String separator = "";
		StringBuilder sb = new StringBuilder();
		Iterator idsIt = arrayIds.iterator();
		sb.append("(");
		while(idsIt.hasNext()){ // For each feature of geoJson
			sb.append(separator);
			sb.append(layersConfig.getIdColumn());
			sb.append("=");
			sb.append("'" + idsIt.next() + "'");
			separator = " OR ";
		};
		sb.append(")");
		return sb.toString();
	}

	public String getIntersection(final RawData geojson, LayersConfig layersConfig) {
		JSONArray jsonRoot = new JSONArray();
		String cqlFilter;
		Filter filter;
		for(LayerConfig layerConfig: layersConfig.getAll()) {
			cqlFilter = getCQLFilterPolygon2(geojson, layerConfig);
			logger.log(Level.SEVERE, "cqlFilter : " + cqlFilter);
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
				while(layerIt.hasNext()){ // For each feature of layer
					SimpleFeature layerFeature = (SimpleFeature) layerIt.next();
					
					if (featureHaveAttribute(layerFeature, layerConfig.getOrganismeColumn())) {
						String organisme = (String) layerFeature.getAttribute(layerConfig.getOrganismeColumn());
						jsOrgansimeArray.add(organisme);
					}
					if (featureHaveAttribute(layerFeature, layerConfig.getIdColumn())) {
						String id = (String) layerFeature.getAttribute(layerConfig.getIdColumn());
						jsIdArray.add(id);
					}				
				}
				JSONObject paramCQL = new JSONObject();
				String cqlFilterAttributs = getCQLFilterAttributs(layerConfig, jsIdArray);
				paramCQL.put("CQL_FILTER", cqlFilterAttributs);
				layerRoot.put("params", paramCQL);
				layerRoot.put("id", jsIdArray);
				layerRoot.put("organisme", jsOrgansimeArray);
				jsonRoot.add(layerRoot);
			}
		};
		return jsonRoot.toString();
	}




	public LayersConfig getLayersList() {
		LayersConfig layers;
		try {
			layers = ConfigLoader.load();
			layers.getAll().forEach(layer -> {
				logger.log(Level.SEVERE, this.catalog.getLayerByName(layer.getLayerName()).getTitle());
				layer.setLayerTitle(this.catalog.getLayerByName(layer.getLayerName()).getTitle());
				//layer.setLayerName(this.catalog.getLayerByName(layer.getLayerName()).getName());
				//this.catalog.getLayerByName(layer.getLayerName()).getResource().
				try {
					logger.log(Level.SEVERE, "Go to get ATTR ");
					FeatureCollection<? extends FeatureType, ? extends Feature> layerFeatures = getFeatureCollectionByLayerName(this.catalog, layer.getLayerName());
					try(FeatureIterator<? extends Feature> layerIt = layerFeatures.features()) {
						Name geomColumn = layerIt.next().getDefaultGeometryProperty().getName();
						logger.log(Level.SEVERE, "---- geomColumn :  " + geomColumn);
						layer.setGeomColumnName(geomColumn.getLocalPart());
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			});
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Cannot load config file", e);
			throw new WPSException("Cannot load config file", e);
		}
		return layers;
	}

	@DescribeResult(name="result", description="GeoJson output result with code organimse for each layer")
	public String execute(
			@DescribeParameter(name = "geometry", min = 0, max = 1, description = "Geojson input to intersect with referential layers and get organisme code",meta = { "mimeTypes=application/json" }) final RawData geojson,
			@DescribeParameter(name = "operation", defaultValue = "getOrganisme", description = "The operation name : getLayerList, getIntersection, getOrganisme") String WPSoperation,
			@DescribeParameter(name = "selectedLayers", min = 0, max = 1, defaultValue = "", description = "The selected layers for intersection, if not set, used all layers") String selectedLayers) {

		logger.log(Level.SEVERE, "operation : " + WPSoperation);
		logger.log(Level.SEVERE, "selectedLayers : " + selectedLayers);


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
				return getIntersection(geojson, layers);
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
		FeatureTypeInfo info = catalog.getFeatureTypeByName(layerName);
		if (Objects.isNull(info)) {
			throw new WPSException(layerName + " : This layerName does not exist - Please update your config File");
		}
		FeatureSource<? extends FeatureType, ? extends Feature> fs = info.getFeatureSource(null, null);
		return fs.getFeatures();
	}
	
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