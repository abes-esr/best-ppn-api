package fr.abes.bestppn.configuration;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Plugin(name = "CustomAppender", category = "core", elementType = Appender.ELEMENT_TYPE)
public class CustomAppender extends AbstractAppender {
    private Map<Long, List<String>> logMessages = new ConcurrentHashMap<>();

    protected CustomAppender(String name, Filter filter) {
        super(name, filter, null);
    }

    @Override
    public void append(LogEvent event) {
        // Récupérer le message de log et l'ajouter à la liste
        StackTraceElement source = event.getSource();
        if (
                (source != null
                        && source.getClassName().contains("BestPpnService")
                        || event.getLoggerName().contains("BestPpnService")
                )
                        && event.getLevel().isMoreSpecificThan(Level.INFO)
        ) {
            String message = event.getMessage().getFormattedMessage();
            Long threadId = event.getThreadId();
            if (this.logMessages.containsKey(threadId)) {
                logMessages.get(threadId).add(message);
            } else {
                List<String> listMessage = new ArrayList<>();
                listMessage.add(message);
                logMessages.put(threadId, listMessage);
            }
        }
    }

    // Méthode pour accéder aux messages de log capturés
    public List<String> getLogMessages(Long threadId) {
        return logMessages.get(threadId);
    }

    // Méthode statique pour créer l'instance de l'appender via le fichier de configuration
    @PluginFactory
    public static CustomAppender createAppender(
            @PluginAttribute("name") String name,
            @PluginElement("Filter") Filter filter) {
        return new CustomAppender(name, filter);
    }

    public void resetLogMessages() {
        logMessages = new ConcurrentHashMap<>();
    }
}
