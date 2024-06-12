// Import Nylas packages
import com.nylas.NylasClient;
import com.nylas.models.Event;
import com.nylas.models.*;

// Import Spark, Jackson and Mustache libraries
import spark.ModelAndView;
import static spark.Spark.*;
import spark.template.mustache.MustacheTemplateEngine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// Import Java libraries
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Date;
import java.util.Map;
import java.util.Objects;

// Import external libraries
import org.apache.commons.codec.digest.HmacUtils;

public class Webhooks {
    // Function to get Hmac
    public static String getHmac(String data, String key) {
        return new HmacUtils("HmacSHA256", key).hmacHex(data);
    }

    public static void main(String[] args) {
        // Array list of Webhooks
        ArrayList<Webhook_Info> array = new ArrayList<Webhook_Info>();

        // Initialize the Nylas client
        NylasClient nylas = new NylasClient.Builder(System.getenv("V3_TOKEN")).build();

        // Default path when we load our web application
        get("/", (request, response) -> {
            // Create a model to pass information to the mustache template
            Map<String, Object> model = new HashMap<>();
            model.put("webhooks", array);
            // Call the mustache template
            return new ModelAndView(model, "show_webhooks.mustache");
        }, new MustacheTemplateEngine());

        // Validate our webhook with the Nylas server
        get("/webhooks", (request, response) ->
                request.queryParams("challenge"));

        // Getting webhook information
        post("/webhooks", (request, response) -> {
                    // Create Json object mapper
                    ObjectMapper mapper = new ObjectMapper();
                    // Read the response body as a Json object
                    JsonNode incoming_webhook = mapper.readValue(request.body(), JsonNode.class);
                    System.out.println("" + incoming_webhook);
                    // Make sure we're reading our calendar
                    //if (Objects.equals(incoming_webhook.get("data").get("object").get("calendar_id").textValue(), System.getenv("CALENDAR_ID"))) {
                        // // Make sure the webhook is coming from Nylas
                        if (getHmac(request.body(), URLEncoder.encode(System.getenv("CLIENT_SECRET"), "UTF-8")).equals(request.headers("X-Nylas-Signature"))) {
                            // Read the event information using the events ednpoint
                            FindEventQueryParams eventquery = new FindEventQueryParams(System.getenv("CALENDAR_ID"));
                            Response<Event> myevent = nylas.events().find(System.getenv("GRANT_ID"), incoming_webhook.get("data").get("object").get("id").textValue(), eventquery);
                            // Read the participants and put them into a single line
                            StringBuilder s_participant = new StringBuilder();
                            for (Participant participant : myevent.getData().getParticipants()) {
                                s_participant.append(";").append(participant.getEmail());
                            }
                            // Get the time of the event. It can be a full day event or have a start and end time
                            // Because of the timezone, we're subtracting 4 hours to the time
                            String event_datetime = "";
                            DateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
                            switch (Objects.requireNonNull(Objects.requireNonNull(myevent.getData().getWhen().getObject()).getValue())) {
                                case "date":
                                    When.Date date = (When.Date) myevent.getData().getWhen();
                                    event_datetime = format.format(new Date(Long.parseLong(date.toString()) * 1000 - (4 * 3600 * 1000)));
                                    break;
                                case "timespan":
                                    When.Timespan timespan = (When.Timespan) myevent.getData().getWhen();
                                    String startDate = format.format(new Date(Long.parseLong(String.valueOf(timespan.getStartTime())) * 1000 - (4 * 3600 * 1000)));
                                    String endDate = format.format(new Date(Long.parseLong(String.valueOf(timespan.getEndTime())) * 1000 - (4 * 3600 * 1000)));
                                    event_datetime = "From " + startDate + " to " + endDate;
                                    break;
                            }
                            // Remove the first ";"
                            if (s_participant.length() > 0) {
                                s_participant = new StringBuilder(s_participant.substring(1));
                            }
                            // Create a new Webhook_Info record
                            Webhook_Info new_webhook = new Webhook_Info();
                            // Fill webhook information
                            new_webhook.setId(incoming_webhook.get("data").get("object").get("id").textValue());
                            new_webhook.setDate(event_datetime);
                            new_webhook.setTitle(myevent.getData().getTitle());
                            new_webhook.setDescription(myevent.getData().getDescription());
                            new_webhook.setParticipants(s_participant.toString());
                            // Make sure value is not null
                            assert myevent.getData().getStatus() != null;
                            new_webhook.setStatus(myevent.getData().getStatus().name());
                            // Add webhook call to an array, so that we display it on screen
                            array.add(new_webhook);
                        }
                    //}
                    return "";
                }
            );
    }
}
