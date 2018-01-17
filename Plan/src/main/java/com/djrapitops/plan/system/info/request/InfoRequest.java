/*
 * Licence is provided in the jar as license.yml also here:
 * https://github.com/Rsl1122/Plan-PlayerAnalytics/blob/master/Plan/src/main/resources/license.yml
 */
package com.djrapitops.plan.system.info.request;

import com.djrapitops.plan.api.exceptions.connection.WebException;
import com.djrapitops.plan.system.webserver.response.Response;

import java.util.Map;

/**
 * //TODO Class Javadoc Comment
 *
 * @author Rsl1122
 */
public interface InfoRequest {

    void placeDataToDatabase() throws WebException;

    Response handleRequest(Map<String, String> variables) throws WebException;

}