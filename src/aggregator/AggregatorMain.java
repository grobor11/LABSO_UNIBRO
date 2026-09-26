package aggregator;

import java.util.Scanner;

/*
 * Punto di avvio principale dell'Aggregator (Master).
 * Questa classe funge da coordinatore del sistema: inizializza le strutture dati condivise,
 * avvia il server di rete su un thread separato per accettare le connessioni dei nodi in background
 * e mantiene attiva l'interfaccia a riga di comando (CLI) sul thread principale per l'amministratore.
 */
public class AggregatorMain{
    public static void main(String[] args) { 
        // Controllo argomenti della riga di comando: l'unico parametro atteso è la porta del server
        if (args.length != 1) {
            System.out.println("Devi usare: java AggregatorMain <porta>");
            System.exit(1); // Chiusura del programma con errore
        }
        
        // Parsing dell'argomento passato da terminale in un altro intero (la porta del server)
        int port = Integer.parseInt(args[0]);

        // Creazione del registro delle risorse e del gestore dei log 
        ResourceRegistry registry = new ResourceRegistry();
        DownloadLogManager logManager = new DownloadLogManager();

        // Creazione e avvio del server AggregatorServer in un thread separato
        // Fondamemtale per permettere al server di accettare connessioni in parallelo all'interfaccia CLI
        AggregatorServer server = new AggregatorServer(port, registry, logManager);
        Thread serverThread = new Thread(server);
        serverThread.start();

        // Avvio dell'interfaccia a riga di comando 
        runCLI(registry, logManager, serverThread);

    }

    //Gestisce i comandi dell'operatore e arresta il server su quit o EOF.
    private static void runCLI(ResourceRegistry registry, DownloadLogManager logManager, Thread serverThread) {
        try (Scanner scan = new Scanner(System.in)) {
            // hasNextLine evita l'eccezione di nextLine quando la console termina (EOF).
            while (scan.hasNextLine()) {
                System.out.println("Comandi disponibili: ");
                System.out.println("1. listdata");
                System.out.println("2. log");
                System.out.println("3. quit");

                String command = scan.nextLine().trim();

                switch(command) {
                    case "listdata":
                        var mappa = registry.getGlobalList();
                        if (mappa.isEmpty()) {
                            System.out.println("Nessuna risorsa registrata al momento.");
                        } else {
                            for (var entry : mappa.entrySet()) {
                                System.out.println("File: " + entry.getKey() + " -> Posseduto da: " + entry.getValue());
                            }
                        }
                        break;

                    case "log":
                        var logList = logManager.getLogs();
                        if (logList.isEmpty()) {
                            System.out.println("Nessun download registrato.");
                        } else {
                            for (String log : logList) {
                                System.out.println(log);
                            }
                        }
                        break;

                    case "quit":
                        System.out.println("Chiusura del server...");
                        stopServer(serverThread);
                        return;

                    default:
                        System.out.println("Comando non riconosciuto. Usa uno dei comandi disponibili.");
                        break;
                }
            }
        }

        System.out.println("Fine input. Chiusura del server...");
        stopServer(serverThread);
    }

    // Richiede l'arresto del server e attende che abbia chiuso le connessioni attive.
    private static void stopServer(Thread serverThread) {
        serverThread.interrupt();
        try {
            serverThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Chiusura forzata");
        }
    }
}


    

