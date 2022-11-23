package org.geoserver.wpsoddc.wps;

import org.geotools.process.factory.DescribeParameter;
import org.geotools.process.factory.DescribeProcess;
import org.geotools.process.factory.DescribeResult;
import org.geoserver.wps.gs.GeoServerProcess;

@DescribeProcess(title="OddcWPS", description="Hello WPS ODDC Sample")
public class OddcWPS implements GeoServerProcess {

   @DescribeResult(name="result", description="output result")
   public String execute(@DescribeParameter(name="name", description="name to return") String name) {
        return "Hello, " + name;
   }
}