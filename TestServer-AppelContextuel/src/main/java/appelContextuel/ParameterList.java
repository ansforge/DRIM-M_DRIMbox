package appelContextuel;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import io.quarkus.logging.Log;
import jakarta.inject.Singleton;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;


@Path("/drim")
@Singleton
public class ParameterList {

	// Map cache with uuid and query parameters associated 
	private final Map<String, Map<String, String>> paramsCache = new HashMap<>();

	// Map of query parameters
	private final Map<String, String> queryParams = new HashMap<>();

	private String[] paramsMandatory = {"Patient.identifier.value", "Patient.identifier.system", "Patient.name.family", "Patient.name.given", "Patient.gender", 
			"Patient.birthDate", "Address.district", "opposition", "patientID", "patientIDIssuer"};  

	private String[] paramsAll = {"Patient.identifier.value", "Patient.identifier.system", "Patient.name.family", "Patient.name.given", "Patient.gender", 
			"Patient.birthDate", "Address.district", "StudyInstanceUID", "Modality", "accessionNumber", "issuer", "studyDate", "anatomicRegion", "situation", "opposition",
			 "patientID", "patientIDIssuer"};  

	/**
	 *  Retrieve params from RIS and adding them to the cache with a uuid 
	 * 
	 * @param requestBody list of params from RIS
	 * @return response 301 to redirect user to frontend
	 * @throws Exception
	 */
	@POST
	@Produces(MediaType.TEXT_PLAIN)
	@Path("/303")
	public Response echo(String requestBody) throws Exception {
		Log.info(requestBody);
		boolean validParams = false;
		queryParams.clear();
		UUID uuid = UUID.randomUUID();
		// Retrieving query parameters from url
		String[] pairs = requestBody.split("&");
		for (String pair : pairs) {
			String[] keyValuePair = pair.split("=");
			if(keyValuePair.length <2) {
				Log.info("parameters without value : " + pair);
				return Response.status(400, "parameters without value : " + pair).build();
			}

			validParams = this.verifExist(keyValuePair[0]); 
			if (!validParams) {
				Log.info("unknown parameter found : " + keyValuePair[0]);
				return Response.status(400, "unknown parameter found : " + keyValuePair[0]).build();
			}

			// Adding query params in local map
			this.queryParams.put(keyValuePair[0], keyValuePair[1]);
		}
		// Adding local map with uuid in Cache map
		this.paramsCache.put(uuid.toString(), this.queryParams);
		for ( String param: paramsMandatory)  
		{  
			validParams = this.verifMandatory(param); 
			if (!validParams) {
				Log.info("missing a mandatory parameter : " + param);
				return Response.status(400, "missing a mandatory parameter : " + param).build();
			}
		}  

		return Response //seeOther = 303 redirect
				.seeOther(UriBuilder.fromUri("/show")
						.queryParam("uuid", uuid.toString())
						.build())//build the URL where you want to redirect
				.build();
			
	}
	
	
	
	/**
	 *  Retrieve params from RIS and adding them to the cache with a uuid 
	 * 
	 * @param requestBody list of params from RIS
	 * @return response 301 to redirect user to frontend
	 * @throws Exception
	 */
	@POST
	@Produces(MediaType.TEXT_PLAIN)
	@Path("/302")
	public Response echoBis(String requestBody) throws Exception {
		Log.info(requestBody);
		boolean validParams = false;
		queryParams.clear();
		UUID uuid = UUID.randomUUID();
		// Retrieving query parameters from url
		String[] pairs = requestBody.split("&");
		for (String pair : pairs) {
			String[] keyValuePair = pair.split("=");
			if(keyValuePair.length <2) {
				Log.info("parameters without value : " + pair);
				return Response.status(400, "parameters without value : " + pair).build();
			}

			validParams = this.verifExist(keyValuePair[0]); 
			if (!validParams) {
				Log.info("unknown parameter found : " + keyValuePair[0]);
				return Response.status(400, "unknown parameter found : " + keyValuePair[0]).build();
			}

			// Adding query params in local map
			this.queryParams.put(keyValuePair[0], keyValuePair[1]);
		}
		// Adding local map with uuid in Cache map
		this.paramsCache.put(uuid.toString(), this.queryParams);
		for ( String param: paramsMandatory)  
		{  
			validParams = this.verifMandatory(param); 
			if (!validParams) {
				Log.info("missing a mandatory parameter : " + param);
				return Response.status(400, "missing a mandatory parameter : " + param).build();
			}
		}  

		return Response //seeOther = 303 redirect
				.status(302).contentLocation(UriBuilder.fromUri("/show").queryParam("uuid", uuid.toString()).build()).build();
			
	}


	private boolean verifMandatory(String value) {
		boolean valid = true;
		if(this.queryParams.get(value) == null) {
			valid = false;
			Log.info(value);
		}
		return valid;
	}

	/**
	 * Return boolean if all parameters here
	 * 
	 * @param value
	 * @return if value accepted, the return true, else false
	 */
	private boolean verifExist(String value) {
		boolean valid = false;
		for ( String param: paramsAll)  
		{  
			if(Objects.equals(param, value))
				valid = true;
		}  
		return valid;
	}
	
	public  Map<String, String> getParams(String uuid){
		return this.paramsCache.get(uuid);
	}
}









