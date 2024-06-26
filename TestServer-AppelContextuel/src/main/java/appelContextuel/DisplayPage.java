package appelContextuel;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/drim/show")
public class DisplayPage {
	
	@Inject ParameterList parameterList;
	
    @Inject
    Template template; 
	
    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance hello(@QueryParam("uuid") String uuidParams) {
        return template.data("params", parameterList.getParams(uuidParams));
    }
}
