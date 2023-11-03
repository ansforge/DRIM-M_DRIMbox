package appelContextuel;

import io.quarkus.logging.Log;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/show")
public class DisplayPage {
	
	@Inject ParameterList parameterList;
	
    @Inject
    Template template; 
	
    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance hello(@QueryParam("uuid") String uuidParams) {
    	Log.info(parameterList.getParams(uuidParams));
        return template.data("params", parameterList.getParams(uuidParams));
    }
}
