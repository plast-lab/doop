package doop
import groovyx.net.http.Method
import groovyx.net.http.RESTClient
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.entity.mime.content.FileBody
import org.apache.http.entity.mime.content.StringBody

import static groovyx.net.http.ContentType.JSON
/**
 * A client for a remote doop server.
 * EXPERIMENTAL.
 *
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 27/11/2014
 */
class Client {

    private Log logger = LogFactory.getLog(getClass())
    private final String url

    Client(String url) {
        this.url = url
    }


    void postNewAnalysis(Analysis analysis) {

        logger.info "Posting new alanysis ${analysis.id} to server: $url"

        MultipartEntityBuilder builder = MultipartEntityBuilder.create()
        builder.addPart("name", new StringBody(analysis.name))
        builder.addPart("jars", new FileBody(analysis.jars[0].resolve()))
        analysis.options.values().each { AnalysisOption option ->
            String val = option.value.toString()
            builder.addPart(option.id, new StringBody(val))
        }

        RESTClient restClient = new RESTClient(url)
        restClient.request(Method.POST, JSON) { req ->
            uri.path = "/analyses"
            requestContentType = "multipart/form-data"
            req.entity = builder.build()

            response.success = { resp ->
                println resp.data
            }
        }
    }
}
