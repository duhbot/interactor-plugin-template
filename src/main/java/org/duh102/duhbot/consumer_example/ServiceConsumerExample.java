package org.duh102.duhbot.consumer_example;

import org.duh102.duhbot.functions.ServiceConsumerPlugin;
import org.duh102.duhbot.functions.ServiceMediator;
import org.duh102.duhbot.functions.ServiceResponse;
import org.duh102.duhbot.service_example.*;
import org.pircbotx.Colors;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.MessageEvent;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ServiceConsumerExample extends ListenerAdapter implements ServiceConsumerPlugin {
    public static final String CONSUMER_NAME = "service-consumer-example";
    private Pattern invPattern = Pattern.compile(".inventory([ \t]+" +
            "(?<command>[^ \t]+)([ \t]+(?<extra>.+))?)?");
    private Pattern changePattern = Pattern.compile("(?<item>\"[^\"]+\"|[^\"]+)"
            + "([ \t]+(?<count>-?[1-9][0-9]*))?");
    String viewCommand = "view", changeCommand = "change";
    Set<String> commandSet = Set.of(new String[]{viewCommand, changeCommand});
    ServiceMediator mediator = null;
    @Override
    public void onMessage(MessageEvent message) {
        try {
            String text =
                    Colors.removeFormattingAndColors(message.getMessage().trim().toLowerCase());
            Matcher invMatcher = invPattern.matcher(text);
            if (invMatcher.matches()) {
                String username = message.getUser().getNick();
                String command = invMatcher.group("command");
                String extra = invMatcher.group("extra");
                if (command != null && !commandSet.contains(command)) {
                    message.respond(String.format("Invalid command %s, must be " +
                                    "one of %s", command,
                            commandSet.stream().sorted().collect(Collectors.joining(
                                    " "))));
                    return;
                } else if (command == null || command.equals(viewCommand)) {
                    Map<String, Integer> inv = getInventory(username);
                    if (inv == null) {
                        message.respond(String.format("Unable to retrieve " +
                                "inventory for %s", username));
                        return;
                    }
                    message.respond(String.format("Inventory for %s: %s",
                            username, displayInventory(inv)));
                } else if (command.equals(changeCommand)) {
                    Matcher changeOpts = null;
                    try {
                        changeOpts = changePattern.matcher(extra);
                    } catch (NullPointerException npe) {
                        //we'll handle this in the next one
                    }
                    if (extra == null || changeOpts == null || !changeOpts.matches()) {
                        message.respond(String.format("Invalid options for " +
                                        "command %1$s, use %1$s [item] (count)",
                                changeCommand));
                        return;
                    }
                    String item = changeOpts.group("item");
                    String countText = changeOpts.group("count");
                    int count = countText == null ? 1 : Integer.parseInt(countText);
                    Map<String, Integer> inv = modifyInventory(username, item,
                            count);
                    if( inv == null ) {
                        message.respond(String.format("Unable to modify " +
                                        "inventory for %s", username));
                        return;
                    }
                    message.respond(String.format("New inventory for %s: %s",
                            username, displayInventory(inv)));
                }
            }
        } catch( InventoryProblem ip ) {
            message.respond(ip.toString());
        }
    }

    @Override
    public void setInteraactionMediator(ServiceMediator serviceMediator) {
        this.mediator = serviceMediator;
    }

    @Override
    public String getPluginName() {
        return CONSUMER_NAME;
    }

    public String displayInventory(Map<String, Integer> invMap) {
        String[] items = invMap.keySet().toArray(new String[0]);
        StringBuilder inventoryList = new StringBuilder();
        Arrays.sort(items);
        ListIterator<String> itemIt = Arrays.asList(items).listIterator();
        while(itemIt.hasNext()) {
            int idx = itemIt.nextIndex();
            String item = itemIt.next();
            int count = invMap.get(item);
            inventoryList.append(String.format("%s: %d", item, count));
            if( idx < items.length )
                inventoryList.append(", ");
        }
        return inventoryList.toString();
    }

    public Map<String, Integer> getInventory(String username) throws InventoryProblem {
        if( mediator == null) {
            return null;
        }
        try {
            ServiceResponse response =
                    mediator.interact(ServiceProviderExample.SERVICE_ENDPOINT,
                            InventoryViewEndpoint.PATH,
                            new InventoryViewRequest(username),
                            InventoryResponse.class);
            InventoryResponse inventoryResponse =
                    (InventoryResponse) response.getResponse();
            return inventoryResponse.get();
        } catch( InventoryProblem ip ) {
            throw ip;
        } catch( Exception e){
            e.printStackTrace();
            return null;
        }
    }

    public Map<String, Integer> modifyInventory(String username,
                                                String itemName,
                                                int countDiff) throws InventoryProblem {
        if( mediator == null) {
            return null;
        }
        try {
            ServiceResponse response =
                    mediator.interact(ServiceProviderExample.SERVICE_ENDPOINT,
                            InventoryChangeEndpoint.PATH,
                            new InventoryChangeRequest(username, itemName,
                                    countDiff),
                            InventoryResponse.class);
            InventoryResponse inventoryResponse =
                    (InventoryResponse)response.getResponse();
            return inventoryResponse.get();
        } catch( InventoryProblem ip ) {
            throw ip;
        } catch( Exception e){
            e.printStackTrace();;
            return null;
        }
    }
}
