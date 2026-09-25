package client;

import common.Protocol;
import java.io.*;
import java.net.*;

/*
 * Punto di avvio del Nodo Sensore.
 * Questa classe si occupa dell'inizializzazione del nodo, stabilendo la connessione
 * con l'Aggregator centrale e avviando due flussi paralleli (multithreading):
 * 1. Il CommandHandler (CLI) per far inserire i comandi all'utente.
 * 2. Il ServerSocket P2P per accettare le richieste di download dagli altri nodi.
 */
public class ClientMain {
    public static void main(String[] args) {

        // Lettura degli argomenti da riga di comando
        // Le specifiche richiedono di passare IP e porta dell'Aggregator all'avvio.
        if (args.length != 2) {
            System.out.println("Errore di sintassi, devi usare: java ClientMain <ip_aggregator> <porta_aggregator>");
            System.exit(1);
        }

        String aggregatorIP = args[0];
        int aggregatorPort = Integer.parseInt(args[1]);

        // Creazione della memoria condivisa per le risorse locali del nodo
        // Essendo thread-safe (synchronized), può essere letta dalla CLI e dal server P2P senza conflitti
        LocalStorage localStorage = new LocalStorage();

        try {
            // Creazione del "mini server" P2P del nodo
            // Inserendo '0' come porta, diciamo al Sistema Operativo di assegnarci 
            // la prima porta libera disponibile. Così possiamo avviare quanti client vogliamo sullo stesso PC.
            ServerSocket p2pServer = new ServerSocket(0);
            int myP2pPort = p2pServer.getLocalPort(); // Recuperiamo la porta assegnata dal SO
            String myIp = InetAddress.getLocalHost().getHostAddress(); // Recuperiamo l'IP locale del nodo

            // Connessione all'aggregator centrale
            Socket aggregatorSocket = new Socket(aggregatorIP, aggregatorPort);
            BufferedReader aggIn = new BufferedReader(new InputStreamReader(aggregatorSocket.getInputStream()));
            PrintWriter aggOut = new PrintWriter(aggregatorSocket.getOutputStream(), true);

            // Registrazione del nodo sull'aggregator
            // Inviamo il comando REGISTER seguito dal nostro IP e dalla porta del nostro mini server
            aggOut.println(Protocol.REGISTER_NODE + " " + myIp + " " + myP2pPort);
            String response = aggIn.readLine();

            if (response == null || !response.startsWith(Protocol.SUCCESS)) {
                System.err.println("Errore: Registazione rifiutata dall'aggregator.");
                System.exit(1);
            }

            System.out.println("Connessione all'aggregator avvenuta con successo!");
            System.out.println("La tua porta P2P locale e': " + myP2pPort);

            //Inizializzazione ed avvio del CommandHandler (Interfaccia utente)
            RobustDownloader downloader = new RobustDownloader(localStorage, aggIn, aggOut);
            CommandHandler cliHandler = new CommandHandler(localStorage, aggregatorSocket, aggIn, aggOut, downloader);

            //Avviamo la CLI in un thread separato così non blocca l'ascolto delle connessioni in arrivo
            Thread cliThread = new Thread(cliHandler);
            cliThread.start();

            // Ciclo infinito di ascolto per le richeste Peer-to-Peer
            //Il thread principale (questo) fa da server per gli altri nodi sensori che vogliono scaricare file.
            while (!Thread.interrupted()) {
                try {
                    
                    // L'accept() blocca il thread in attesa che un altro client si connetta 
                    Socket peerSocket = p2pServer.accept();

                    //Appena un nodo si connette, avviamo un thread al volo per inviargli i file richiesto
                    new Thread(() -> handlerPeerRequest(peerSocket, localStorage)).start();

                } catch (IOException e) {
                    if (!p2pServer.isClosed()) {
                        System.err.println("Errore nell'accettazione della connessione P2P.");

                    }
                    break; // Uscita dal ciclo se il server socket è chiuso o c'è un errore

                }
            }

        } catch (IOException e) {
            System.err.println("Impossibile connettersi all'Aggregator (" + aggregatorIP + ":" + aggregatorPort + "). Verificare che sia acceso.");
            System.exit(1);
        }

        
    }

    /*
     * Metodo di supporto che gestisce la richiesta di un altro nodo.
     * Quando un Peer si connette a noi, ci chiede un file. Noi lo cerchiamo nel LocalStorage e glielo inviamo.
     */
    private static void handlerPeerRequest(Socket peerSocket, LocalStorage localStorage) {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(peerSocket.getInputStream()));
            PrintWriter out = new PrintWriter(peerSocket.getOutputStream(), true)
        ) {
            String request = in.readLine();

            if (request != null && request.startsWith(Protocol.PEER_DOWNLOAD_REQUEST)) {
                String[] parti = request.split(" ");
                String resourceName = parti[1];

                if(localStorage.hasData(resourceName)) {
                    String contenuto = localStorage.getData(resourceName);
                    out.println(Protocol.PEER_DATA + " " + contenuto);
                    System.out.println("\n[P2P Server] Inviata risorsa '" + resourceName + " ' a un peer remoto. \n> ");

                } else {
                    out.println(Protocol.ERROR + " Risorsa non trovata");

                }
                }
            } catch (IOException e) {
                System.err.println("Errore durante l'invio della risorsa al peer.");
        } finally {
            try {
                peerSocket.close();
            } catch (IOException ignored) {}
        }
    }
}
