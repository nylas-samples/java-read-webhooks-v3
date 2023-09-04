// Import Nylas packages
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nylas.NylasClient;
import com.nylas.models.Event;

// Import Spark and Mustache libraries
import com.nylas.models.*;
import org.apache.commons.codec.digest.HmacUtils;
import spark.ModelAndView;
import static spark.Spark.*;
import spark.template.mustache.MustacheTemplateEngine;

import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Date;
import java.util.Map;
import java.util.Objects;

public class Webhooks {
    public static String getHmac(String data, String key) {
        return new HmacUtils("HmacSHA256", key).hmacHex(data);
    }

    public static void main(String[] args) {
        ArrayList<Webhook_Info> array = new ArrayList<Webhook_Info>();

        // Initialize the Nylas client
        NylasClient nylas = new NylasClient.Builder(System.getenv("V3_TOKEN")).apiUri(System.getenv("BASE_URL")).build();

        // Default path when we load our web application
        get("/", (request, response) -> {
            // Create a model to pass information to the mustache template
            Map<String, Object> model = new HashMap<>();
            model.put("webhooks", array);
            // Call the mustache template
            return new ModelAndView(model, "show_webhooks.mustache");
        }, new MustacheTemplateEngine());

        get("/webhook", (request, response) ->
                request.queryParams("challenge"));

        post("/webhook", (request, response) -> {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode incoming_webhook = mapper.readValue(request.body(), JsonNode.class);
            System.out.println("Calendar Id: " + incoming_webhook.get("data").get("object").get("calendar_id").textValue() + " End of Calendar Id");
            System.out.println("Env Calendar Id: " + System.getenv("CALENDAR_ID") + " End of Env Calendar Id");            
            if(Objects.equals(incoming_webhook.get("data").get("object").get("calendar_id").textValue(), System.getenv("CALENDAR_ID"))){
                System.out.println("Secret: " + getHmac(request.body(), URLEncoder.encode(System.getenv("CLIENT_SECRET"), "UTF-8")) + " End of Secret");
                System.out.println("Signature: " + request.headers("X-Nylas-Signature") + " End of Signature");                
                if(getHmac(request.body(), URLEncoder.encode(System.getenv("CLIENT_SECRET"), "UTF-8")).equals(request.headers("X-Nylas-Signature"))){
                    FindEventQueryParams eventquery = new FindEventQueryParams(System.getenv("CALENDAR_ID"));
                    Response<Event> myevent =  nylas.events().find(System.getenv("GRANT_ID"), incoming_webhook.get("data").get("object").get("id").textValue(),eventquery);
                    StringBuilder s_participant = new StringBuilder();
                    for(Participant participant: myevent.getData().getParticipants()){
                        s_participant.append(";").append(participant.getEmail());
                    }
                    String event_datetime = "";
                    DateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
                    switch (Objects.requireNonNull(Objects.requireNonNull(myevent.getData().getWhen().getObject()).getValue())) {
                        case "date":
                            When.Date date = (When.Date) myevent.getData().getWhen();
                            event_datetime = format.format(date);
                            break;
                        case "timespan":
                            When.Timespan timespan = (When.Timespan) myevent.getData().getWhen();
                            String startDate = format.format(timespan.getStartTime());
                            String endDate = format.format(timespan.getEndTime());
                            event_datetime = "From " + startDate + " to " + endDate;
                            break;
                    }
                    if(s_participant.length() > 0){
                        s_participant = new StringBuilder(s_participant.substring(1));    
                    }
                    Webhook_Info new_webhook = new Webhook_Info();
                    new_webhook.setId(incoming_webhook.get("data").get("object").get("id").textValue());
                    new_webhook.setDate(event_datetime);
                    new_webhook.setTitle(myevent.getData().getTitle());
                    new_webhook.setDescription(myevent.getData().getDescription());
                    new_webhook.setParticipants(s_participant.toString());
                    new_webhook.setStatus(myevent.getData().getStatus().name());
                    array.add(new_webhook);
                }
            }
            Map<String, Object> model = new HashMap<>();
            model.put("webhooks", array);
            return new ModelAndView(model, "show_webhooks.mustache");
        }, new MustacheTemplateEngine());
    }
}
