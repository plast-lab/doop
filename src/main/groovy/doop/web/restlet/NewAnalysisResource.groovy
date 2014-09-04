package doop.web.restlet

import doop.AnalysisOption
import doop.Doop
/**
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 4/9/2014
 */
class NewAnalysisResource extends VelocityResource {

    @Override
    protected void prepareVelocityContext(Map context) {
        super.prepareVelocityContext(context)

        List<AnalysisOption> uiOptions = Doop.ANALYSIS_OPTIONS.findAll { AnalysisOption option ->
            option.definedByUser
        }.sort { AnalysisOption option ->
            option.name
        }

        context.put("options", uiOptions)

        /*
        List<String> analyses = []
        new File(Doop.doopLogic).eachDir { File dir ->
            if (dir.getName().indexOf("sensitive") != -1 ) {
                File f = new File(dir, "analysis.logic")
                if (f.exists() && f.isFile()) {
                    analyses.push(dir.getName())
                }
            }

        }

        context.put("analyses", analyses)
        */
    }


}
