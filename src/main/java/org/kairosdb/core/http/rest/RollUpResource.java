package org.kairosdb.core.http.rest;

import com.google.inject.Inject;
import org.kairosdb.core.formatter.DataFormatter;
import org.kairosdb.core.formatter.FormatterException;
import org.kairosdb.core.http.rest.json.ErrorResponse;
import org.kairosdb.core.http.rest.json.GsonParser;
import org.kairosdb.core.http.rest.json.JsonResponseBuilder;
import org.kairosdb.core.http.rest.json.ValidationErrors;
import org.kairosdb.rollup.RollUpException;
import org.kairosdb.rollup.RollUpManager;
import org.kairosdb.rollup.RollUpTask;
import org.kairosdb.rollup.RollUpTasksStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

@Path("/api/v1/rollups")
public class RollUpResource
{
	private static final Logger logger = LoggerFactory.getLogger(MetricsResource.class);

	private final GsonParser parser;
	private final RollUpTasksStore store;

	@Inject
	public RollUpResource(GsonParser parser, RollUpManager manager, RollUpTasksStore store)
	{
		this.parser = checkNotNull(parser);
		this.store = checkNotNull(store);
	}

	/**
	 Creates roll up tasks from the specified JSON.
	 <p/>
	 Returns information in JSON format about the created tasks. For example:
	 <p/>
	 <pre>
	 "rollup_tasks": [
	 {
	 "id": "393939393",
	 "name": "foo",
	 "attributes":
	 {
	 "url": "/api/v1/rollups/393939393"
	 }
	 },
	 {
	 "id": "12345",
	 "name": "bar",
	 "attributes":
	 {
	 "url": "/api/v1/rollups/12345"
	 }
	 }
	 ]
	 </pre>

	 @param json tasks in json format
	 @return information about the created tasks
	 */
	@POST
	@Produces(MediaType.APPLICATION_JSON + "; charset=UTF-8")
	@Path("/rollup")
	public Response create(String json)
	{
		try
		{
			List<RollUpTask> tasks = parser.parseRollUpTask(json);
			//			manager.addTasks(tasks);

			List<RollupResponse> rollup_tasks = new ArrayList<RollupResponse>();
			for (RollUpTask task : tasks)
			{
				rollup_tasks.add(new RollupResponse(task.getId(), "todo", "/api/v1/rollups/" + task.getId()));
			}

			store.write(tasks);

//			Response.ok(parser.getGson().toJson(rollup_tasks), MediaType.APPLICATION_JSON).build();
			Response.ResponseBuilder responseBuilder = Response.status(Response.Status.OK).entity(
					parser.getGson().toJson(rollup_tasks));
			setHeaders(responseBuilder);
			return responseBuilder.build();
		}
		catch (BeanValidationException e)
		{
			JsonResponseBuilder builder = new JsonResponseBuilder(Response.Status.BAD_REQUEST);
			return builder.addErrors(e.getErrorMessages()).build();
		}
		catch (RollUpException e) // todo combine with Exception?
		{
			logger.error("Failed to add roll ups.", e);
			return setHeaders(Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new ErrorResponse(e.getMessage()))).build();
		}
		catch (Exception e)
		{
			logger.error("Failed to add metric.", e);
			return setHeaders(Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new ErrorResponse(e.getMessage()))).build();
		}
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON + "; charset=UTF-8")
	@Path("rollup")
	public Response list()
	{
		try
		{
			// todo need to check for null and return empty list
			List<RollUpTask> tasks = store.read();
			String json = parser.getGson().toJson(tasks);
			Response.ResponseBuilder responseBuilder = Response.status(Response.Status.OK).entity(json);
			setHeaders(responseBuilder);
			return responseBuilder.build();
		}
		catch (RollUpException e)
		{
			// todo
			logger.error("Failed to list roll ups.", e);
			return setHeaders(Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new ErrorResponse(e.getMessage()))).build();
		}
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON + "; charset=UTF-8")
	@Path("rollup/{id}")
	public Response get(@PathParam("id") String id)
	{
		ValidationErrors validationErrors = null;

		JsonResponseBuilder builder = new JsonResponseBuilder(Response.Status.BAD_REQUEST);
		for (String errorMessage : validationErrors.getErrors())
		{
			builder.addError(errorMessage);
		}
		return builder.build();
	}

	@DELETE
	@Produces(MediaType.APPLICATION_JSON + "; charset=UTF-8")
	@Path("delete/{id}")
	public Response delete(@PathParam("id") String id) throws Exception
	{
		//		manager.removeTask(id);
		return setHeaders(Response.status(Response.Status.NO_CONTENT)).build();
	}

	@PUT
	@Produces(MediaType.APPLICATION_JSON + "; charset=UTF-8")
	@Path("update/{id}")
	public Response update(@PathParam("id") String id, String json)
	{
		return setHeaders(Response.status(Response.Status.NO_CONTENT)).build();
	}

	// todo also used in Metrics Resource.Should this be defined in a common place
	private Response.ResponseBuilder setHeaders(Response.ResponseBuilder responseBuilder)
	{
		responseBuilder.header("Access-Control-Allow-Origin", "*");
		responseBuilder.header("Pragma", "no-cache");
		responseBuilder.header("Cache-Control", "no-cache");
		responseBuilder.header("Expires", 0);

		return (responseBuilder);
	}

	// todo also used in Metrics Resource.Should this be defined in a common place
	public class ValuesStreamingOutput implements StreamingOutput
	{
		private DataFormatter m_formatter;
		private Iterable<String> m_values;

		public ValuesStreamingOutput(DataFormatter formatter, Iterable<String> values)
		{
			m_formatter = formatter;
			m_values = values;
		}

		@SuppressWarnings("ResultOfMethodCallIgnored")
		public void write(OutputStream output) throws IOException, WebApplicationException
		{
			Writer writer = new OutputStreamWriter(output, "UTF-8");

			try
			{
				m_formatter.format(writer, m_values);
			}
			catch (FormatterException e)
			{
				logger.error("Description of what failed:", e);
			}

			writer.flush();
		}
	}

	private class RollupResponse
	{
		private String id;
		private String name;
		private Map<String, String> attributes = new HashMap<String, String>();

		private RollupResponse(String id, String name, String url)
		{
			this.id = id;
			this.name = name;
			attributes.put("url", url);
		}
	}
}
