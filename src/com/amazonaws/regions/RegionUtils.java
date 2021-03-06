/*
 * Copyright 2013-2013 Amazon Technologies, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *    http://aws.amazon.com/apache2.0
 *
 * This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
 * OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and
 * limitations under the License.
 */
package com.amazonaws.regions;

import static com.amazonaws.SDKGlobalConfiguration.REGIONS_FILE_OVERRIDE_SYSTEM_PROPERTY;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.amazonaws.util.VersionInfoUtils;

/**
 * Utilities for working with regions.
 */
public class RegionUtils {

    // If all things failed, this is the fall back config file. 
    private static final String FALLBACK = "regions.xml";
    
    private static List<Region> regions;

    // Use the same logger as the http client
    private static final Log log = LogFactory.getLog("com.amazonaws.request");

    /**
     * Returns a list of the available AWS regions.
     */
    public synchronized static List<Region> getRegions() {
        if ( regions == null ) {
            init();
        }

        return regions;
    }

    /**
     * Returns a list of the regions that support the service given.
     *
     * @see ServiceAbbreviations
     */
    public synchronized static List<Region> getRegionsForService(String serviceAbbreviation) {
        List<Region> regions = new LinkedList<Region>();
        for ( Region r : getRegions() ) {
            if ( r.isServiceSupported(serviceAbbreviation) ) {
                regions.add(r);
            }
        }
        return regions;
    }

    /**
     * Returns the region with the id given, if it exists. Otherwise, returns
     * null.
     */
    public static Region getRegion(String regionName) {
        for ( Region r : getRegions() ) {
            if ( r.getName().equals(regionName) ) {
                return r;
            }
        }

        return null;
    }

    /**
     * Searches through all known regions to find one with any service at the
     * specified endpoint. If no region is found with a service at that
     * endpoint, an exception is thrown.
     *
     * @param endpoint
     *            The endpoint for any service residing in the desired region.
     * @return The region containing any service running at the specified
     *         endpoint, otherwise an exception is thrown if no region is found
     *         with a service at the specified endpoint.
     * @throws MalformedURLException
     *             If the given URL is malformed, or if the one of the service
     *             URLs on record is malformed.
     */
    public static Region getRegionByEndpoint(String endpoint) {
    	URI targetEndpointUri = getUriByEndpoint(endpoint);
    	String targetHost = targetEndpointUri.getHost();

        for ( Region region : getRegions() ) {
            for ( String serviceEndpoint : region.getServiceEndpoints().values() ) {
                URI serviceEndpointUrl = getUriByEndpoint(serviceEndpoint);
                
                if ( serviceEndpointUrl.getHost().equals(targetHost) )
                    return region;
            }
        }

        throw new IllegalArgumentException("No region found with any service for endpoint " + endpoint);
    }

    /**
     * Fetches the most recent version of the regions file from the remote
     * source and caches it to the workspace metadata directory, then
     * initializes the static list of regions with it.
     */
    public static synchronized void init() {
        if ( System.getProperty(REGIONS_FILE_OVERRIDE_SYSTEM_PROPERTY) != null ) {
            try {
                loadRegionsFromOverrideFile();
            } catch ( FileNotFoundException e ) {
                throw new RuntimeException("Couldn't find regions override file specified", e);
            }
        }

        // Fall back onto the version we ship with the SDK
        if ( regions == null ) {
            initSDKRegions();
        }
        // Throw out RuntimeException explicitly
        if ( regions == null ) {
            throw new RuntimeException("Failed to initialize the regions.");
        }
    }

    private static void loadRegionsFromOverrideFile() throws FileNotFoundException {
        String overrideFilePath = System.getProperty(REGIONS_FILE_OVERRIDE_SYSTEM_PROPERTY);
        if ( log.isDebugEnabled() ) {
            log.debug("Using local override of the regions file (" 
                        + overrideFilePath
                        + ") to initiate regions data...");
        }
        File regionsFile = new File(overrideFilePath);
        FileInputStream override = new FileInputStream(regionsFile);
        // Disable endpoint verification
        initRegions(override, false);
    }

    /**
     * Tries to initialize the regions list from the stream given.
     * 
     * @param regionsFile
     *            The input stream pointing to the retrieved region file.
     *            
     * @param enableEndpointVerification
     *            Whether to verify each endpoint when parsing the regions file.
     *            (This should be disabled when regions file override is being
     *            used.)
     */
    private static void initRegions(InputStream regionsFile, boolean enableEndpointVerification) {
        try {
            RegionMetadataParser parser = new RegionMetadataParser();
            regions = parser.parseRegionMetadata(regionsFile, enableEndpointVerification);
        } catch ( Exception e ) {
            log.warn("Failed to parse regional endpoints", e);
        }
    }

    /**
     * Failsafe method to initialize the regions list from the list bundled with
     * the SDK, in case it cannot be fetched from the remote source.
     */
    private static void initSDKRegions() {
        if ( log.isDebugEnabled() ) {
            log.debug("Initializing the regions from the region file bundled with the SDK...");
        }
        InputStream inputStream = RegionUtils.class.getResourceAsStream(FALLBACK);
        initRegions(inputStream, true);
    }

    /**
     * Get the URI object for the given endpoint. URI class cannot correctly parse the endpoint
     * if it doesn't include protocol. This method will add the protocol if this happens.
     */
    private static URI getUriByEndpoint(String endpoint) {
        URI targetEndpointUri= null;
        try {
            targetEndpointUri = new URI(endpoint);
            if (targetEndpointUri.getHost() == null) {
                targetEndpointUri = new URI("http://" + endpoint);
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException("Unable to parse service endpoint: " + e.getMessage());
        }
        return targetEndpointUri;
    }
}
