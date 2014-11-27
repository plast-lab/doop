package doop.web.restlet.api

import org.restlet.Application
import org.restlet.Restlet
import org.restlet.routing.Router

/**
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 25/11/2014
 */
class Endpoints extends Application {

    @Override
    Restlet createInboundRoot() {
        Router router = new Router(getContext())

        router.attach "/analyses", Analyses
        router.attach "/analyses/{id}", Analysis
    }
}
