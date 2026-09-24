package client;


import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;

//classe che gestisce l'archivio locale di rilevazioni del nodo sensore

public class LocalStorage {
    private final Map<String, String> storage;
    
    public LocalStorage(){
        this.storage=new HashMap<>();
    }

    //Aggiunge una rilevazione all'archivio del nodo sensore
    //usato per il comando "add" o al termine di un download fallito
    public synchronized void addData(String nomeRisorsa, String contenuto){
        if(nomeRisorsa==null || contenuto==null)
            throw new IllegalArgumentException("nome risorsa e contenuto non possono essere nulli");
        storage.put(nomeRisorsa, contenuto);
    }

    //Restituisce una rilevazione cercandola per nome
    public synchronized String getData(String nomeRisorsa){
        return storage.get(nomeRisorsa);
    }

    //restituisce true se una rilevazione esiste nell'archivio del nodo
    //altrimenti false
    public synchronized boolean hasData(String nomeRisorsa){
        return storage.containsKey(nomeRisorsa);
    }

    //restituisce il set di tutti i nomi delle rilevazioni in memoria
    //usato per il comando "listdata local" e per comunicare le risorse all'Aggregator
    public synchronized HashSet<String> getAllKeys(){
        return new HashSet<>(storage.keySet());
    }


}


