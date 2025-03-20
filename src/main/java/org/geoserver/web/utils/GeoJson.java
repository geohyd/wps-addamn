package org.geoserver.web.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import org.geoserver.wps.process.RawData;
import org.geotools.data.DataUtilities;
import org.geotools.data.geojson.GeoJSONReader;
import org.geotools.data.geojson.GeoJSONWriter;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.CRS;
import org.geotools.api.feature.Feature;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.NoSuchAuthorityCodeException;
import org.geotools.api.referencing.crs.CRSAuthorityFactory;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import java.io.ByteArrayOutputStream;

public class GeoJson  implements Iterator<Feature>{
	/**
	 * GeoJson class read a RawData GeoJson (or InputStream) from the input WPS argument
	 * Implement an Iterator for loop over all Feature of GeoJson
	 * And offer 3 functions for edit the feature (immutable element by default)
	 * 		We create new feature for this and need to be re-write
	 */

	private FeatureCollection<?, ?> featureCollection;
	private SimpleFeatureTypeBuilder ftBuilder;
	private List<SimpleFeature> newFeatures;
	private SimpleFeatureType nSchema;
	private FeatureIterator<?> fIterator = null;
	
	@Override
	public boolean hasNext() {
		if (Objects.isNull(fIterator)) {
			fIterator = featureCollection.features();
		}
		return fIterator.hasNext();
	}

	@Override
	public Feature next() {
		return fIterator.next();
	}
	
	public void resetIterator() {
		fIterator = featureCollection.features();
	}

	/**
	 * Read a geojson RawData
	 * @param geojson A RawData reference to geojson
	 * @throws IOException if getInputStream doesn't work on the RawData or a readFeatureCollection error
	 */
	public void readGeoJson(RawData geojson) throws IOException {
		this.readGeoJson(geojson.getInputStream());
	}

	/**
	 * Read a geojson InputStream
	 * @param geojson A InputStream reference to geojson
	 * @throws IOException if readFeatureCollection raise an error
	 */
	public void readGeoJson(InputStream geojson) throws IOException {
		try (GeoJSONReader reader = new GeoJSONReader(geojson)) {
			this.featureCollection = reader.getFeatures();
		}
	}

	/**
	 * Create a new Feature Builder for edit each feature and add some attributes
	 * @param attributes A list of new attribut
	 */
	public void createFeatureBuilder(List<String> attributes) {
		//TODO : Need to be rewrite with newFeature and toGeoJson for a more generique method
		this.ftBuilder = new SimpleFeatureTypeBuilder();
		SimpleFeatureType schema = (SimpleFeatureType) this.featureCollection.getSchema();
		ftBuilder.setName(schema.getName());
		ftBuilder.setSuperType((SimpleFeatureType) schema.getSuper());
		ftBuilder.addAll(schema.getAttributeDescriptors());
		attributes.forEach(attr -> ftBuilder.add(attr, List.class));
		this.nSchema = ftBuilder.buildFeatureType();
		this.newFeatures = new ArrayList<>();
	}
	
	/**
	 * Add a new feature to the FeatureBuilder based on the feature param
	 * @param feature The feature to add at the FeatureBuilder
	 * @return The reference to the new (and editable) Feature
	 */
	public SimpleFeature newFeature(SimpleFeature feature) {
		SimpleFeature nF = DataUtilities.reType(nSchema, feature);
		this.newFeatures.add(nF);
		return nF;
	}

	/**
	 * Return the FeatureBuilder as a geojson
	 * @return The geojson String
	 * @throws IOException if toString methode doesn't work
	 */
	public String toGeoJson() throws IOException {
		DefaultFeatureCollection featureCollection = new DefaultFeatureCollection(null, null);
		((DefaultFeatureCollection) featureCollection).addAll(this.newFeatures);
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		try (GeoJSONWriter jsonWriter = new GeoJSONWriter(outputStream)) {
			jsonWriter.writeFeatureCollection(featureCollection);
		}
		return outputStream.toString("UTF-8");
	}

	/**
	 * Get the CRS of the current geojson, default 4326 if not find
	 * @return The CRS
	 * @throws NoSuchAuthorityCodeException if creation Factory don't find the default 4326 CRS
	 * @throws FactoryException  if CRS Factory failed
	 */
	public CoordinateReferenceSystem getCRS() throws NoSuchAuthorityCodeException, FactoryException {
		CoordinateReferenceSystem geojsonCRS = this.featureCollection.getSchema().getCoordinateReferenceSystem();
		if (Objects.isNull(geojsonCRS)) {
			CRSAuthorityFactory   factory = CRS.getAuthorityFactory(true);
			return factory.createCoordinateReferenceSystem("EPSG:4326");
		}else {
			return geojsonCRS;
		}
	}
}
