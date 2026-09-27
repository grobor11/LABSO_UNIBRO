package aggregator;

import common.PeerInfo;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
/*
Gestisce lo storico (log) di tutte le operazioni di download eseguite sulla rete.
È una risorsa condivisa protetta: i metodi sono 'synchronized' per garantire 
la mutua esclusione durante la scrittura e la lettura dei log da parte dei diversi thread.
 */
public class DownloadLogManager {

    private static final DateTimeFormatter LOG_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private List<String> logs;

    public DownloadLogManager(){
        this.logs = new ArrayList<>();
    }

//Registra un nuovo evento di download nello storico.
    public synchronized void addLog(PeerInfo downloader, PeerInfo uploader, String resourceName, boolean success) {
        String risultato;
         if (success == true) {
            risultato = "COMPLETATO";
            } else {
                risultato = "FALLITO";
    }
    // Costruisce la stringa di log formattata con data/ora e i dettagli dell'operazione
    String orario = LocalDateTime.now().format(LOG_TIMESTAMP_FORMAT);
    String nuovaRiga = "[" + orario + "] Il nodo " + downloader + 
                   " ha scaricato il file '" + resourceName + 
                   "' dal nodo " + uploader + ". Esito: " + risultato;

    logs.add(nuovaRiga);
}

//Restituisce lo storico completo dei log.
public synchronized List<String> getLogs() {
    return new ArrayList<>(logs);
}
    
}

