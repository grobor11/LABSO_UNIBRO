package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Set;

/*
 * Gestisce i comandi inseriti dall'utente nel nodo sensore.
 *
 * La classe legge i comandi dalla console e coordina le operazioni tra
 * l'archivio locale, l'Aggregator e il downloader delle risorse remote.
 * Viene eseguita in un thread separato rispetto alla comunicazione di rete.
 */
public class CommandHandler implements Runnable {

    // Archivio locale delle rilevazioni del nodo sensore.
    private final LocalStorage localStorage;

    // Socket e stream usati per comunicare con l'Aggregator.
    private final Socket aggregatorSocket;
    private final BufferedReader aggregatorIn;
    private final PrintWriter aggregatorOut;

    // Gestore del download delle risorse dagli altri nodi.
    private final RobustDownloader robustDownloader;

    // Indica se il ciclo di lettura dei comandi deve rimanere attivo.
    private volatile boolean running = true;

    // Riceve tutte le risorse necessarie per eseguire i comandi dell'utente.
    public CommandHandler(LocalStorage localStorage, Socket aggregatorSocket, 
                          BufferedReader aggregatorIn, PrintWriter aggregatorOut, 
                          RobustDownloader robustDownloader) {
        this.localStorage = localStorage;
        this.aggregatorSocket = aggregatorSocket;
        this.aggregatorIn = aggregatorIn;
        this.aggregatorOut = aggregatorOut;
        this.robustDownloader = robustDownloader;
    }

    @Override
    public void run() {
        // Legge continuamente i comandi dalla console finché il client è attivo.
        try (BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in))) {
            while (running) {
                System.out.print("> ");
                String inputLine = consoleReader.readLine();

                if (inputLine == null) {
                    break;
                }

                inputLine = inputLine.trim();
                if (inputLine.isEmpty()) {
                    continue;
                }

                handleCommand(inputLine);
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("Errore di lettura da tastiera: " + e.getMessage());
            }
        }
    }

    // Analizza il comando principale e delega l'operazione al metodo appropriato.
    private void handleCommand(String inputLine) {
        String[] tokens = inputLine.split("\\s+");
        String mainCmd = tokens[0].toLowerCase();

        switch (mainCmd) {
            case "listdata":
                if (tokens.length < 2) {
                    System.out.println("Uso non valido. Sintassi: listdata <local|remote>");
                    return;
                }
                String scope = tokens[1].toLowerCase();
                if (scope.equals("local")) {
                    handleListDataLocal();
                } else if (scope.equals("remote")) {
                    handleListDataRemote();
                } else {
                    System.out.println("Opzione non valida. Usa 'local' o 'remote'.");
                }
                break;

            case "add":
                if (tokens.length < 3) {
                    System.out.println("Uso non valido. Sintassi: add <nome_risorsa> <contenuto>");
                    return;
                }
                String resourceName = tokens[1];
                // Ricostruisce l'eventuale contenuto con spazi interni
                String content = inputLine.substring(inputLine.indexOf(tokens[2]));
                handleAdd(resourceName, content);
                break;

            case "download":
                if (tokens.length < 2) {
                    System.out.println("Uso non valido. Sintassi: download <nome_risorsa>");
                    return;
                }
                handleDownload(tokens[1]);
                break;

            case "quit":
                handleQuit();
                break;

            default:
                System.out.println("Comando non riconosciuto. Comandi validi: listdata local, listdata remote, add, download, quit");
                break;
        }
    }

    // Mostra le rilevazioni salvate nell'archivio locale del nodo.
    private void handleListDataLocal() {
        Set<String> keys = localStorage.getAllKeys();
        System.out.println("Risorse:");
        if (keys.isEmpty()) {
            System.out.println(" (nessuna rilevazione salvata localmente)");
        } else {
            for (String key : keys) {
                System.out.println(" - " + key + ": " + localStorage.getData(key));
            }
        }
    }

    // Richiede all'Aggregator l'elenco delle rilevazioni disponibili sui nodi remoti.
    private void handleListDataRemote() {
        try {
            aggregatorOut.println("LISTDATA_REMOTE");
            // Legge le righe inviate dall'Aggregator fino al messaggio di terminazione.
            String line;
            while ((line = aggregatorIn.readLine()) != null) {
                if (line.equals("END") || line.equals("OK")) {
                    break;
                }
                System.out.println(line);
            }
        } catch (IOException e) {
            System.err.println("Errore di comunicazione con l'Aggregator: " + e.getMessage());
        }
    }

    // Salva una rilevazione localmente e comunica all'Aggregator la nuova risorsa.
    private void handleAdd(String resourceName, String content) {
        localStorage.addData(resourceName, content);
        System.out.println("Rilevazione '" + resourceName + "' salvata in locale.");

        // Invia una notifica all'Aggregator per registrare la nuova risorsa.
        aggregatorOut.println("ADD " + resourceName);
        try {
            String ack = aggregatorIn.readLine();
            if (ack != null && ack.startsWith("OK")) {
                System.out.println("Rilevazione registrata sull'Aggregator.");
            }
        } catch (IOException e) {
            System.err.println("Errore nella notifica all'Aggregator: " + e.getMessage());
        }
    }

    // Avvia il download della risorsa richiesta tramite il downloader robusto.
    private void handleDownload(String resourceName) {
        if (robustDownloader != null) {
            robustDownloader.downloadResource(resourceName);
        } else {
            System.out.println("RobustDownloader non ancora inizializzato.");
        }
    }

    // Arresta il client, avvisa l'Aggregator e chiude la connessione di rete.
    private void handleQuit() {
        running = false;
        aggregatorOut.println("QUIT");
        try {
            if (aggregatorSocket != null && !aggregatorSocket.isClosed()) {
                aggregatorSocket.close();
            }
        } catch (IOException ignored) {}
        System.out.println("Chiusura in corso...");
        System.exit(0);
    }
}