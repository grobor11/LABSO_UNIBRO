package aggregator;

import common.PeerInfo;
import common.Protocol;
import java.io.*;
import java.net.Socket;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;

/**
 * Gestisce la comunicazione diretta con un singolo nodo sensore (Client).
 * Implementa Runnable perché il server istanzia un nuovo Thread per ogni client connesso.
 * Fa da "ponte" tra i messaggi di rete in arrivo e le memorie condivise del server.
 */
public class ClientHandler implements Runnable {

    private static final Semaphore downloadPermit = new Semaphore(1, true);
    
    // Il canale di comunicazione fisico con il nodo remoto
    private Socket socket;
    
    // Riferimenti alle memorie centrali condivise (Thread-Safe)
    private ResourceRegistry registry;
    private DownloadLogManager logManager;

    // Variabile di stato: memorizza l'identità del nodo che sta parlando con questo thread.
    // È fondamentale per poter rimuovere le sue risorse se si scollega improvvisamente.
    private PeerInfo connectedNode = null;
    private String activeTokenId = null;
    private boolean voluntaryDisconnect = false;

    // Il costruttore riceve il socket aperto dal Server e i riferimenti alle memorie centrali.
    public ClientHandler(Socket socket, ResourceRegistry registry, DownloadLogManager logManager) {
        this.socket = socket;
        this.registry = registry;
        this.logManager = logManager;
    }

    @Override
    public void run() {
        System.out.println("Avvio gestione nodo remoto in un nuovo thread...");

        // Usiamo il try-with-resources per aprire i flussi di Input (lettura) e Output (scrittura).
        // Il BufferedReader legge riga per riga (ottimo per messaggi di testo),
        // Il PrintWriter invia testo. Il parametro 'true' abilita l'autoflush (invio immediato dei dati).
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
        ) {
            String request;
            
            // Ciclo di ascolto: il thread si blocca su in.readLine() in attesa di un messaggio.
            // Il ciclo si ferma se riceve una interrupt o se il client si disconnette (in.readLine() ritorna null).
            while (!Thread.interrupted() && (request = in.readLine()) != null) {
                
                // Il protocollo prevede messaggi testuali separati da spazi.
                // Es: "REGISTER 192.168.1.5 8000"
                String[] parti = request.trim().split("\\s+");
                String comando = parti[0]; // La prima parola è sempre il tipo di comando

                // 1. REGISTRAZIONE NODO
                if (comando.equals(Protocol.REGISTER_NODE)) {
                    // Estraiamo IP e porta dalle parole successive
                    String ip = parti[1];
                    int port = Integer.parseInt(parti[2]);
                    
                    // Salviamo l'identità del nodo in questo Thread
                    this.connectedNode = new PeerInfo(ip, port);
                    
                    out.println(Protocol.SUCCESS); // Rispondiamo con un "OK"
                } 

                // 2. AGGIUNTA DI UNA RISORSA
                else if (comando.equals(Protocol.UPDATE_RESOURCES)) {
                    // Controllo di sicurezza: il nodo deve essersi registrato prima di aggiungere file
                    if (this.connectedNode != null) {
                        String resourceName = parti[1];
                        // Salviamo il file nella mappa condivisa
                        registry.addResource(resourceName, this.connectedNode);
                        
                        out.println(Protocol.SUCCESS);
                    } else {
                        out.println(Protocol.ERROR + " Devi prima registrarti con " + Protocol.REGISTER_NODE);
                    }
                }
            
                // 3. RICHIESTA LISTA GLOBALE DELLE RISORSE
                else if (comando.equals(Protocol.REQUEST_GLOBAL_LIST)) {
                    // Chiediamo al registry la mappa completa. (Essendo thread-safe, non ci sono conflitti)
                    Map<String, Set<PeerInfo>> mappa = registry.getGlobalList();
                    
                    // Scorriamo la mappa: per ogni file, elenchiamo tutti i nodi che lo possiedono
                    for (Map.Entry<String, Set<PeerInfo>> entry : mappa.entrySet()) {
                        String resourceName = entry.getKey();
                        
                        for (PeerInfo peer : entry.getValue()) {
                            // Inviamo più righe al client.
                            // Formato: DATA <nome_file> <ip> <porta>
                            out.println(Protocol.PEER_DATA + " " + resourceName + " " + peer.getIp() + " " + peer.getPort());
                        }
                    }
                    // Avvisiamo il client che abbiamo finito di trasmettere la lista
                    out.println(Protocol.LIST_END);
                }

                // 4. RICHIESTA DI UN PEER PER IL DOWNLOAD
                else if (comando.equals(Protocol.REQUEST_DOWNLOAD)) {
                    if (parti.length != 2 || connectedNode == null || activeTokenId != null) {
                        out.println(Protocol.ERROR + " Richiesta di download non valida");
                        continue;
                    }

                    downloadPermit.acquire();
                    Set<PeerInfo> peers = registry.getPeersForResource(parti[1]);
                    peers.remove(connectedNode);
                    if (peers.isEmpty()) {
                        downloadPermit.release();
                        out.println(Protocol.NOT_FOUND);
                    } else {
                        PeerInfo selectedPeer = peers.iterator().next();
                        activeTokenId = UUID.randomUUID().toString();
                        out.println(Protocol.TOKEN_GRANTED + " " + activeTokenId + " "
                                + selectedPeer.getIp() + " " + selectedPeer.getPort());
                    }
                }
        
                // 5. SEGNALAZIONE DOWNLOAD RIUSCITO
                else if (comando.equals(Protocol.REPORT_DOWNLOAD_SUCCESS)) {
                    if (this.connectedNode != null && parti.length == 4 && activeTokenId != null) {
                        // Formato: DOWNLOAD_SUCCESS <nome_risorsa> <ip_peer> <porta_peer>
                        String resourceName = parti[1];
                        String upIp = parti[2];
                        int upPort;
                        try {
                            upPort = Integer.parseInt(parti[3]);
                        } catch (NumberFormatException e) {
                            out.println(Protocol.ERROR + " Porta peer non valida");
                            continue;
                        }

                        logManager.addLog(this.connectedNode,
                                new PeerInfo(upIp, upPort), resourceName, true);
                        out.println(Protocol.SUCCESS);
                    } else {
                        out.println(Protocol.ERROR + " Segnalazione di successo non valida");
                    }
                }

                // 6. SEGNALAZIONE DOWNLOAD FALLITO
                else if (comando.equals(Protocol.REPORT_DOWNLOAD_FAILED)) {
                    if (this.connectedNode != null && parti.length == 4 && activeTokenId != null) {
                        // Formato ricevuto: DOWNLOAD_FAIL <nome_risorsa> <ip_peer> <porta_peer>
                        String resourceName = parti[1];
                        String upIp = parti[2];
                        int upPort;
                        try {
                            upPort = Integer.parseInt(parti[3]);
                        } catch (NumberFormatException e) {
                            out.println(Protocol.ERROR + " Porta peer non valida");
                            continue;
                        }
                        
                        PeerInfo uploader = new PeerInfo(upIp, upPort);
                        registry.removeResource(resourceName, uploader);
                        
                        // Scriviamo nel log condiviso che questo download è fallito
                        logManager.addLog(this.connectedNode, uploader, resourceName, false);
                        
                        out.println(Protocol.SUCCESS);
                    } else {
                        out.println(Protocol.ERROR + " Segnalazione di fallimento non valida");
                    }
                }

                // 7. RILASCIO DEL TOKEN DI DOWNLOAD
                else if (comando.equals(Protocol.RELEASE_TOKEN)) {
                    if (parti.length == 3 && activeTokenId != null && activeTokenId.equals(parti[1])) {
                        activeTokenId = null;
                        downloadPermit.release();
                        out.println(Protocol.SUCCESS);
                    } else {
                        out.println(Protocol.ERROR + " Token non valido");
                    }
                }

                // 8. DISCONNESSIONE VOLONTARIA
                else if (comando.equals(Protocol.UNREGISTER_NODE)) {
                    out.println(Protocol.SUCCESS);
                    voluntaryDisconnect = true;
                    break; // Uscendo dal while, finiamo dritti nel blocco finally per la pulizia
                }
                
                // 9. COMANDO SCONOSCIUTO
                else {
                    out.println(Protocol.ERROR + " Comando non riconosciuto");
                }
            }
        } catch (Exception e) {
            // Se un nodo "crasha" o stacca il cavo di rete, entra in questa eccezione
            System.err.println("Connessione persa o errore con un client: " + e.getMessage());
        } finally {
            if (activeTokenId != null) {
                activeTokenId = null;
                downloadPermit.release();
            }

            // IL blocco FINALLY: Fault Tolerance e pulizia (Cleanup)
            // Viene eseguito sempre, sia in caso di uscita volontaria sia per errore di rete.
            
            // Se il nodo aveva fatto il login (connectedNode != null), 
            // rimuoviamo tutti i suoi file dalla lista centrale.
            // Così evitiamo che altri client cerchino di scaricare da un nodo ormai morto.
            if (this.connectedNode != null && !voluntaryDisconnect) {
                System.out.println("Disconnessione rilevata. Pulizia risorse per il nodo: " + this.connectedNode);
                
                // Iteriamo su tutti i file per assicurarci di rimuovere questo peer ovunque
                Map<String, Set<PeerInfo>> mappa = registry.getGlobalList();
                for (String resourceName : mappa.keySet()) {
                    registry.removeResource(resourceName, this.connectedNode);
                }
            }
            
            // Chiusura sicura del canale di rete
            try {
                socket.close();
                System.out.println("Socket chiuso correttamente.");
            } catch (IOException e) {
                System.err.println("Errore durante la chiusura del socket.");
            }
        }
    }
}