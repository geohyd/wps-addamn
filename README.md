# wps-addamn
WPS script for GeoServer for CGDD/SDES

# Intro
This GeoServer WPS was create for a project with CGDD/SDES
It get a geojson as input, get all layers (filter by a config file) who intersect geojson feature and return here organisme key

# Installation
- Copy/Paste the addamn_wps-2.18.jar in geoserver/WEB-INF/lib/ directory.
- Create folder <GeoServer/data_dir>/wps_addamn
- Create file <GeoServer/data_dir>/wps_addamn/config.xml
- In <GeoServer/data_dir>/wps_addamn/config.xml, add layers list and organisme column name (show Config file structure chapter)
- restart GeoServer

_If the config.xml was not found by the script when you call it, an empty config.xml will be create with a example structure_

# Config file structure
The file config.xml is mandatory for this WPS and allow to set the list of layers which will be used for intersections.

In this file, each layer have a layerName and organismeColumn.

Exemple : 

```
<layers>
  <layer>
    <layerName>my_layer_name</layerName>
    <organismeColumn>my_column_to_return_if_intersect</organismeColumn>
  </layer>
  <layer>
    <layerName>my_second_layer_name</layerName>
    <organismeColumn>column_to_return</organismeColumn>
  </layer>
</layers>
```

# Final example
So, if you call this script with a GeoJson which 2 Features, this script read each geojson Feature, read each layers in config file, and get all Feature with valid intersection (org.geotools.coverage.util.IntersectUtils.intersects); create a new property to the geojson Feature with name as the layername and value as an array ; and finaly add to this array all the organismeColumn who layer feature intersect.


# For developer

## Init Eclipse project
_based on https://docs.geoserver.org/latest/en/developer/programming-guide/ows-services/implementing.html_

- Clone the geoserver project from github.
- In src\community, clone this project
- in community/pom.xml, add the profile for this WPS :
```
<profile>
  <id>addamn_wps</id>
  <modules>
    <module>addamn_wps</module>
  </modules>
</profile>
```
- in web/app/pom.xml, add the dependency :
```
<dependency>
    <groupId>org.geoserver</groupId>
    <artifactId>addamn_wps</artifactId>
    <version>2.18</version>
</dependency>
```
- in web/app, launch `mvn install` (be patient)
- in web/app, launch `mvn -P wps,addamn_wps eclipse:eclipse`
- Setup GeoServer in eclipse as [describe here](https://docs.geoserver.org/latest/en/developer/quickstart/eclipse.html#quickstart-eclipse)
- Start Start.java in gs-web-app


## After each code change
- Stop Geoserver
- Refresh project gs-web-app (right clik/refresh)
- Start Geoserver

## TODO
There is some todo in code and need to implement unittest