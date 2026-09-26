package client;

import common.Protocol;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

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
    private final PeerServer peerServer;

    // Indica se il ciclo di lettura dei comandi deve rimanere attivo.
    private volatile boolean isRunning = true;

    // Riceve tutte le risorse necessarie per eseguire i comandi dell'utente.
    public CommandHandler(LocalStorage localStorage, Socket aggregatorSocket, 
                          BufferedReader aggregatorIn, PrintWriter aggregatorOut, 
                          RobustDownloader robustDownloader, PeerServer peerServer) {
        this.localStorage = localStorage;
        this.aggregatorSocket = aggregatorSocket;
        this.aggregatorIn = aggregatorIn;
        this.aggregatorOut = aggregatorOut;
        this.robustDownloader = robustDownloader;
        this.peerServer = peerServer;
    }

    @Override
    public void run() {
        // Legge continuamente i comandi dalla console finché il client è attivo.
        try (BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in))) {
            while (isRunning) {
                System.out.print("> ");
                String inputLine = consoleReader.readLine();

                if (inputLine == null) {
                    handleDisconnect();
                    return;
                }

                inputLine = inputLine.trim();
                if (inputLine.isEmpty()) {
                    continue;
                }

                handleCommand(inputLine);
            }
        } catch (IOException e) {
            if (isRunning) {
                System.err.println("Errore di lettura da tastiera: " + e.getMessage());
            }
        }
    }

    // Analizza il comando principale e delega l'operazione al metodo appropriato.
    private void handleCommand(String commandLine) {
        String[] commandTokens = commandLine.split("\\s+");
        String commandName = commandTokens[0].toLowerCase();

        switch (commandName) {
            case "listdata":
                if (commandTokens.length < 2) {
                    System.out.println("Uso non valido. Sintassi: listdata <local|remote>");
                    return;
                }
                String resourceScope = commandTokens[1].toLowerCase();
                if (resourceScope.equals("local")) {
                    handleLocalResourceList();
                } else if (resourceScope.equals("remote")) {
                    handleRemoteResourceList();
                } else {
                    System.out.println("Opzione non valida. Usa 'local' o 'remote'.");
                }
                break;

            case "add":
                if (commandTokens.length < 3) {
                    System.out.println("Uso non valido. Sintassi: add <nome_risorsa> <contenuto>");
                    return;
                }
                String resourceName = commandTokens[1];
                // Ricostruisce l'eventuale contenuto con spazi interni
                String resourceContent = commandLine.substring(commandLine.indexOf(commandTokens[2]));
                handleAddResource(resourceName, resourceContent);
                break;

            case "download":
                if (commandTokens.length < 2) {
                    System.out.println("Uso non valido. Sintassi: download <nome_risorsa>");
                    return;
                }
                handleDownloadResource(commandTokens[1]);
                break;

            case "quit":
                handleDisconnect();
                break;

            default:
                System.out.println("Comando non riconosciuto. Comandi validi: listdata local, listdata remote, add, download, quit");
                break;
        }
    }

    // Mostra le rilevazioni salvate nell'archivio locale del nodo.
    private void handleLocalResourceList() {
        Set<String> resourceNames = new TreeSet<>(localStorage.getAllKeys());
        System.out.println("Risorse:");
        for (String resourceName : resourceNames) {
            System.out.println("- " + resourceName);
        }
    }

    // Richiede all'Aggregator l'elenco delle rilevazioni disponibili sui nodi remoti.
    private void handleRemoteResourceList() {
        Map<String, Set<String>> resources = new TreeMap<>();
        try {
            aggregatorOut.println(Protocol.REQUEST_GLOBAL_LIST);
            String line;
            while ((line = aggregatorIn.readLine()) != null) {
                if (line.equals(Protocol.LIST_END)) {
                    break;
                }

                String[] fields = line.split("\\s+");
                if (fields.length == 4 && fields[0].equals(Protocol.PEER_DATA)) {
                    String peerAddress = fields[2] + ":" + fields[3];
                    resources.computeIfAbsent(fields[1], key -> new TreeSet<>()).add(peerAddress);
                } else if (line.startsWith(Protocol.ERROR)) {
                    System.err.println(line);
                    return;
                } else {
                    System.err.println("Risposta non valida dall'Aggregator: " + line);
                    return;
                }
            }
        } catch (IOException e) {
            System.err.println("Errore di comunicazione con l'Aggregator: " + e.getMessage());
            return;
        }

        System.out.println("Risorse:");
        if (resources.isEmpty()) {
            System.out.println("Nessuna risorsa disponibile sulla rete.");
            return;
        }
        for (Map.Entry<String, Set<String>> entry : resources.entrySet()) {
            System.out.println("- " + entry.getKey() + ": " + String.join(", ", entry.getValue()));
        }
    }

    // Salva una rilevazione localmente e comunica all'Aggregator la nuova risorsa.
    private void handleAddResource(String resourceName, String resourceContent) {
        localStorage.addData(resourceName, resourceContent);
        System.out.println("Rilevazione '" + resourceName + "' salvata in locale.");

        // Invia una notifica all'Aggregator per registrare la nuova risorsa.
        aggregatorOut.println(Protocol.UPDATE_RESOURCES + " " + resourceName);
        try {
            String ack = aggregatorIn.readLine();
            if (ack != null && ack.startsWith(Protocol.SUCCESS)) {
                System.out.println("Rilevazione registrata sull'Aggregator.");
            }
        } catch (IOException e) {
            System.err.println("Errore nella notifica all'Aggregator: " + e.getMessage());
        }
    }

    // Avvia il download della risorsa richiesta tramite il downloader robusto.
    private void handleDownloadResource(String resourceName) {
        if (robustDownloader != null) {
            robustDownloader.downloadResource(resourceName);
        } else {
            System.out.println("RobustDownloader non ancora inizializzato.");
        }
    }

    // Arresta il client, avvisa l'Aggregator e chiude la connessione di rete.
    private void handleDisconnect() {
        isRunning = false;
        peerServer.stop();
        aggregatorOut.println(Protocol.UNREGISTER_NODE);
        try {
            if (aggregatorSocket != null && !aggregatorSocket.isClosed()) {
                aggregatorSocket.close();
            }
        } catch (IOException ignored) {}
        System.out.println("Chiusura in corso...");
        System.exit(0);
    }
}