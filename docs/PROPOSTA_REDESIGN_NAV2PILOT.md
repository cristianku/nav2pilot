# Nav2Pilot — proposta di revisione grafica Android e iOS

Data: 18 ottobre 2026. Stato: **proposta da valutare, nessuna modifica alle app applicata**.

## Obiettivo

Rendere Nav2Pilot e Nav2Pilot iOS più curate, riconoscibili e immediate da usare. Cristian chiede una veste da app finita, con il logo visibile anche all'interno. La funzione centrale deve emergere subito: scegliere un luogo e inviarlo al proprio comma.

Questo documento propone interventi grafici e di presentazione dei flussi esistenti. Palette, misure, testi e disposizione sono proposte, non decisioni già approvate. Non introduce nuove funzioni di navigazione, nuovi servizi o nuove dipendenze.

## Base della revisione e limiti

Sono stati esaminati gli screenshot presenti in entrambi i repository, gli asset del logo e il codice corrente delle schermate, comprese le modifiche locali già presenti. Gli screenshot documentano una versione salvata: non sono una verifica visiva della build attuale.

Riferimenti principali:

- Android: [invio](screenshots/sent.png), [dispositivi](screenshots/devices.png), `Nav2PilotRoot.kt`, `SendScreen.kt`, `FavoritesScreen.kt`, `DevicesScreen.kt`, `ui/theme/Theme.kt`.
- iOS, nel repository affiancato `nav2pilot-ios`: `docs/screenshots/send.png`, `sent.png`, `devices.png`, `App/SendView.swift`, `App/DevicesView.swift`, `App/FavoritesView.swift`, `App/RouteSection.swift`, `Shared/Components.swift`, `ShareExtension/ShareViewController.swift`.
- Logo Android: `app/src/main/res/drawable-xxxhdpi/ic_launcher_foreground.png`.
- Logo iOS: `App/Assets.xcassets/AppIcon.appiconset/AppIcon.png`.

Non sono state compilate o avviate le app. Nessun contatto con il comma, nessun invio di destinazioni o token. Il lavoro esistente nei repository resta fuori dall'intervento.

## Cosa rende oggi l'interfaccia troppo tecnica

| Osservazione | Effetto sull'aspetto | Proposta |
| --- | --- | --- |
| Il marchio è rappresentato dal solo testo Nav2Pilot nelle schermate principali. | L'identità del logo si perde appena si apre l'app. | Inserire il logo nell'intestazione della schermata principale e riprenderlo nelle informazioni dell'app. |
| Coordinate, provenienza del dato, dongle ID, versione e branch sono molto presenti. | La schermata sembra uno strumento di diagnosi. | Portare in primo piano luogo, indirizzo e dispositivo scelto; raccogliere i dettagli in sezioni espandibili. |
| Su Android diverse superfici hanno una tinta lilla/grigia; su iOS prevale la struttura a sezioni di un `Form`. | Il risultato appare generico e lontano dal logo blu/ciano. | Superfici neutre, accenti mirati e una composizione dedicata alla schermata di invio. |
| Ricerca, apertura nelle mappe, configurazione Mapbox, invio e cancellazione competono per lo spazio. | È difficile capire qual è il passo principale. | Una sola azione primaria, con azioni accessorie più discrete. |
| Nello screenshot iOS la proposta di configurare Mapbox viene prima del dispositivo e dell'invio. | Una configurazione opzionale allunga il percorso visivo verso il pulsante principale. | Compattare il richiamo e tenere l'invio raggiungibile senza attraversare il blocco di configurazione. |
| La scheda dispositivi espone token, storage sicuro, backend e nomi RPC. | La gestione quotidiana assume il tono di una pagina per sviluppatori. | Separare dispositivi, account, mappe e informazioni tecniche con una gerarchia leggibile. |
| Nei preferiti restano coordinate e accenti blu non sempre derivati dal tema comune. | Le righe risultano dense e la coerenza cromatica è fragile. | Nome e indirizzo come contenuto principale; colore di invio condiviso con il resto dell'app. |

## Direzioni possibili

1. **Identità condivisa, controlli nativi — consigliata.** Riprendere il logo, comporre una home dedicata, usare un tema comune e conservare convenzioni iOS e Android. Offre un cambiamento evidente con interventi concentrati sulla UI.
2. **Ritocco conservativo.** Aggiungere logo, sistemare colori e ridurre i testi lasciando quasi tutti i layout attuali. Impatto contenuto, ma su iOS rimarrebbe in gran parte l'aspetto da modulo.
3. **Mappa a tutto schermo.** Mettere una mappa al centro dell'esperienza. Più vicina a un navigatore, ma su Android richiederebbe funzionalità oggi assenti e porterebbe il lavoro oltre una revisione grafica. Non consigliata per questa richiesta.

La proposta seguente sviluppa la prima direzione.

## 1. Logo presente dentro l'app

- **Schermata principale:** logo accanto al nome Nav2Pilot in un'intestazione compatta. Dimensione proposta del riquadro: 36–40 pt/dp. Evitare un secondo titolo enorme con lo stesso nome.
- **Preferiti e Dispositivi:** mantenere il titolo della sezione come elemento principale. Il logo non deve essere ripetuto in ogni scheda o diventare una filigrana.
- **Informazioni dell'app:** piccolo blocco in fondo a Dispositivi con logo da 64–72 pt/dp, nome e versione. Eventuale nota di indipendenza da comma.ai e sunnypilot in questa sede.
- **Estensione di condivisione iOS:** logo da 24–28 pt accanto al nome, se lo spazio disponibile lo consente; destinazione e pulsante di invio restano prioritari.
- Usare l'illustrazione attuale strada/freccia su blu scuro. Non è necessario ridisegnare il marchio per ottenere questa revisione.
- Predisporre un asset interno esplicito: per esempio `BrandLogo.imageset` su iOS e `brand_logo` nelle risorse Android. L'asset per il launcher ha esigenze diverse da un'immagine dentro l'app.
- Il foreground Android attuale contiene molto spazio attorno al simbolo: riusarlo interamente in un riquadro piccolo renderebbe il logo poco leggibile. Preparare, in un eventuale intervento successivo, un'esportazione con inquadratura coerente con l'icona iOS, senza deformare o tagliare strada e freccia.
- Conservare il fondale scuro del logo come piccolo riquadro anche nel tema chiaro; non estenderlo a tutta la schermata. Nessuno splash prolungato o animazione obbligatoria.

## 2. Linguaggio visivo comune

### Palette proposta

| Ruolo | Tema chiaro | Tema scuro |
| --- | --- | --- |
| Fondo generale | `#F5F7FB` | `#0B1220` |
| Superficie delle schede | `#FFFFFF` | `#141F31` |
| Testo principale | `#162238` | `#F2F6FC` |
| Testo secondario | `#556477` | `#AAB8CC` |
| Pulsante principale | `#465BEA`, testo bianco | `#8C9BF7`, testo `#10182A` |
| Accento del marchio | `#007C96` su superfici chiare | `#00D5E8` su superfici scure |

Conservare il blu di invio già presente permette continuità; il ciano richiama il logo. Usare il ciano brillante soprattutto per piccoli dettagli decorativi, non per testi su bianco. Questi valori sono candidati: le combinazioni effettive richiedono verifica del contrasto, inclusi stati disabilitati, icone e bordi.

Successo, avviso ed errore devono avere colori semantici separati dal marchio, accompagnati sempre da un'icona e da un testo. Evitare grandi riempimenti colorati per ogni blocco.

### Spazi, caratteri e componenti

- Margini laterali proposti: 20 pt/dp; spaziatura basata su 4, 8, 12, 16, 24 e 32.
- Schede con raggio indicativo di 16–20 pt/dp, ricerca e pulsanti di 12–16. Bordi sottili o separazione tonale; ombre leggere solo dove aiutano a distinguere i piani.
- Caratteri di sistema: SF su iOS, tipografia Material su Android. Nessun font aggiuntivo.
- Titolo del luogo 22–24, testo principale 16–17, metadati 13–14 come riferimenti iniziali, rispettando Dynamic Type e scala caratteri Android.
- Un solo stile di pulsante primario e uno secondario in tutta l'app. Eliminare colori blu locali indipendenti dal tema, come quelli dell'invio nei preferiti.
- Conservare tab bar native e tre sezioni. Proposta di etichette: **Invia / Preferiti / Dispositivi** se si approva la localizzazione italiana; gli esempi italiani qui servono a descrivere il tono, non autorizzano una traduzione parziale dell'app.
- Icona dispositivo/auto per Dispositivi su entrambe le piattaforme; attualmente Android usa un ingranaggio e iOS un'auto.

## 3. Schermata Invia: luogo e azione al centro

Ordine visivo proposto nello stato con destinazione selezionata:

```text
[logo] Nav2Pilot

Dove vuoi andare?
[ Cerca un luogo o incolla un link          ]
                               Incolla link

DESTINAZIONE
Duomo di Milano                         ♡  ⋯
Indirizzo del luogo, se disponibile
Apri nelle mappe                     Dettagli

[auto] Peugeot 3008                       ▾
       Online · verificato alle 10:18

[ Percorso e tappe — solo iOS, se presenti ]

┌ Area di invio sopra la barra delle schede ┐
│ [           Invia a Peugeot 3008        ] │
│ ✓ Destinazione inviata · 10:18             │
└───────────────────────────────────────────┘

       Invia       Preferiti       Dispositivi
```

Schema descrittivo: non è uno screenshot né un mockup verificato. L'orario di verifica compare soltanto se effettivamente disponibile; la sua introduzione va valutata separatamente se richiede un nuovo dato di stato.

### Ricerca e destinazione

- Una ricerca visivamente unitaria, con icona, cancellazione e caricamento integrati. Invio da tastiera e comando di ricerca restano disponibili; il pulsante per incollare è secondario e chiaramente riconoscibile.
- Placeholder più corto, per esempio “Cerca un luogo o incolla un link”. La possibilità di inserire coordinate va indicata nell'aiuto del campo, senza perderne il supporto.
- Risultati con nome evidente e indirizzo sotto. Coordinate mostrate quando sono l'unica descrizione disponibile, evitando duplicazioni.
- Destinazione come scheda principale: nome, eventuale indirizzo e cuore. Spostare coordinate e provenienza sotto “Dettagli”. Se il luogo non ha un nome, le coordinate restano il titolo.
- L'avviso di posizione approssimativa deve rimanere visibile sulla scheda: è un'informazione necessaria per decidere se inviare.
- Su iOS raccogliere Apple Maps e Google Maps in un'azione “Apri nelle mappe” con scelta successiva, evitando due grandi righe prima dell'invio. Android conserva l'apertura nelle app supportate dal flusso attuale.
- Distinguere “Rimuovi dalla selezione” da “Cancella sul dispositivo”: sono azioni con effetti diversi. La seconda va in un menu secondario chiaramente etichettato, raggiungibile anche senza destinazione locale selezionata.

### Dispositivo e invio

- Selettore compatto con icona, alias del dispositivo e stato sintetico. Dongle ID, versione e branch nella scheda dettagli.
- “Online”, “Non raggiungibile”, “Verifica in corso” e “Da verificare” devono restare distinti. Un ping precedente non autorizza a mostrare il dispositivo come raggiungibile adesso.
- Pulsante primario pieno, altezza indicativa 52–56 pt/dp, in un'area ancorata sopra la tab bar. Contenuto scorrevole con spazio inferiore sufficiente per evitare sovrapposizioni.
- A testo ingrandito, con tastiera o su schermi piccoli, l'area può adattarsi e scorrere: leggibilità e accesso ai contenuti prevalgono sul posizionamento fisso.
- Durante l'invio mantenere il testo leggibile con un indicatore di avanzamento. Dopo la risposta, feedback breve vicino al pulsante, persistente finché pertinente.
- “Destinazione inviata” conferma l'esito disponibile; non deve diventare “Navigazione avviata” senza una prova di quello stato.
- Se manca un requisito, spiegarlo vicino al comando: per esempio “Collega il tuo account comma” o “Seleziona un dispositivo”. Le condizioni reali di abilitazione non vanno cambiate nell'ambito del solo restyling.

## 4. Percorso iOS: valorizzare la mappa già esistente

- Conservare percorso, alternative e tappe già presenti. Dare alla mappa una scheda dedicata con angoli coerenti, senza racchiuderla visivamente in un modulo di impostazioni.
- Con Mapbox configurato, evidenziare il percorso selezionato e presentare durata/distanza in righe o piccole schede leggibili. Le altre alternative restano visibili con minore enfasi.
- Numerare le tappe e rendere chiara la loro rimozione. Mantenere un suggerimento breve per l'aggiunta tramite pressione prolungata.
- Con Mapbox assente, usare un richiamo compatto “Configura Mapbox”, con spiegazione disponibile su richiesta. Non mostrare una mappa finta o dati di percorso inventati.
- L'invio resta raggiungibile mentre si consulta la mappa. Non rappresentare la semplice consegna della destinazione come prova che il comma seguirà l'anteprima.
- Mantenere attribuzioni e controlli del fornitore della mappa accessibili e non coperti dal pulsante fisso.
- Android non ha la stessa anteprima nel codice UI esaminato: il suo restyling non deve introdurre questa funzione implicitamente.

## 5. Preferiti: elenco curato e rapido

- Righe più ariose con icona luogo, nome e indirizzo quando disponibile. Coordinate nei dettagli, salvo quando sono l'unica identificazione del luogo.
- Pulsante di invio compatto, coerente con il tema e con etichetta accessibile che identifichi destinazione e dispositivo.
- Conservare la scelta del preferito per rivederlo e l'invio diretto già esistente. Non introdurre un nuovo passaggio obbligatorio.
- Conservare la rimozione con gesto; proporre anche un'azione esplicita nel menu per chi non usa i gesti.
- Stato vuoto composto al centro con icona cuore, titolo “I tuoi luoghi, a portata di mano” e una sola frase che spiega come aggiungerli.
- Non aggiungere categorie Casa/Lavoro, cronologia o suggerimenti automatici: sarebbero nuove funzioni da discutere separatamente.

## 6. Dispositivi: gestione comprensibile

Ordine proposto:

1. **I tuoi dispositivi.** Schede con alias, tipo se noto, selezione ben visibile e aggiornamento elenco. ID e informazioni tecniche nel dettaglio.
2. **Account comma.** Stato delle credenziali, email se già disponibile e scadenza quando leggibile. “Gestisci accesso” apre i controlli esistenti per sostituire o rimuovere il token.
3. **Mappe e percorsi.** Stato Mapbox e accesso alla configurazione. Dichiarare chiaramente gli effetti reali di aggiunta/rimozione delle credenziali, anche sul dispositivo quando previsti dal comportamento corrente.
4. **Avanzate.** Aggiunta manuale tramite dongle ID, dettagli di connessione e backend. Conservare le funzioni senza esporre RPC e spiegazioni lunghe nella vista iniziale.
5. **Informazioni.** Logo, nome, versione e nota sul progetto indipendente.

“Credenziali salvate” è più preciso di “Account connesso” quando non è stata verificata la validità corrente. “Accesso da aggiornare” va usato per una credenziale scaduta o rifiutata. Non proporre un login automatico inesistente.

La configurazione dei token deve mantenere istruzioni reali, mascheramento, incolla esplicito, verifica e salvataggio. Le spiegazioni su Keychain/Keystore possono andare in “Come sono protetti i dati”. Un messaggio breve nella vista iniziale non deve nascondere errori o effetti della rimozione.

Non mostrare sunnylink come scelta operativa: il backend esaminato non è implementato. L'informazione può restare in Avanzate.

## 7. Stati vuoti, errori e piccoli dettagli

| Stato | Presentazione proposta |
| --- | --- |
| Primo utilizzo senza account | Logo, frase breve sullo scopo e azione “Collega account comma”, che apre il flusso esistente. |
| Nessuna destinazione | Invito a cercare o condividere un luogo dalle mappe, senza schede tecniche vuote. |
| Ricerca in corso | Indicatore nel campo e contenuto stabile, evitando salti dell'intera schermata. |
| Nessun risultato | Messaggio breve con invito a provare un indirizzo più preciso. |
| Dispositivo non raggiungibile | Icona e testo vicino al dispositivo, con possibilità di verificare di nuovo. |
| Credenziali rifiutate | Messaggio comprensibile e accesso alla sostituzione; dettaglio tecnico consultabile. |
| Invio riuscito | Spunta, nome del dispositivo e orario; nessun pannello enorme. |
| Invio fallito | Messaggio contestuale persistente, dettaglio disponibile e possibilità di riprovare, mantenendo la destinazione. |

Microanimazioni discrete per selezione e feedback, rispettando Riduci movimento. Nessuna animazione del logo che ritardi l'uso. L'interfaccia deve rimanere comprensibile senza colore, suono o feedback aptico.

## 8. Applicazione sulle due piattaforme

| Area | Android | iOS |
| --- | --- | --- |
| Identità e navigazione | `ui/Nav2PilotRoot.kt`, risorse logo interne | `App/Nav2PilotApp.swift`, asset logo per app ed estensione |
| Tema | `ui/theme/Theme.kt`: ruoli cromatici, tipografia e forme | Asset colori e componenti SwiftUI comuni, compatibili con iOS 17+ |
| Home e azioni | `ui/screens/SendScreen.kt` | `App/SendView.swift`, `Shared/Components.swift` |
| Percorsi | Conservare le funzioni attuali | `App/RouteSection.swift` |
| Preferiti | `ui/screens/FavoritesScreen.kt` | `App/FavoritesView.swift` |
| Gestione dispositivi | `ui/screens/DevicesScreen.kt` | `App/DevicesView.swift` |
| Condivisione | Allineare la presentazione della schermata aperta dal contenuto condiviso | `ShareExtension/ShareViewController.swift` e componenti condivisi |

Percorsi Android relativi a `app/src/main/java/com/cristianku/nav2pilot/`. Questa tabella identifica le aree di un eventuale intervento futuro; non descrive file modificati da questa proposta.

Su iOS conviene mantenere le liste native nelle schermate di gestione e comporre la home con sezioni dedicate. Su Android conviene mantenere Material 3 e definirne meglio tema e gerarchia. La coerenza fra le app riguarda marchio, priorità e linguaggio; non richiede controlli identici al pixel.

## 9. Priorità proposte

| Priorità | Intervento | Risultato atteso |
| --- | --- | --- |
| P1 | Logo interno, palette, intestazione e gerarchia della home | Identità riconoscibile appena si apre l'app. |
| P1 | Destinazione più leggibile, dispositivo compatto, invio sempre raggiungibile | Azione principale evidente. |
| P1 | Dettagli tecnici raccolti e feedback sintetici | Riduzione dell'aspetto da pannello di sviluppo. |
| P2 | Revisione Preferiti e Dispositivi | Qualità uniforme nelle schermate secondarie. |
| P2 | Percorso iOS ed estensione di condivisione coerenti | Esperienza curata anche fuori dal flusso principale. |
| P3 | Rifinitura di stati vuoti, transizioni e accessibilità | Maggiore chiarezza e qualità percepita nell'uso quotidiano. |

L'accessibilità deve essere rispettata già da P1; P3 indica la rifinitura complessiva, non il rinvio dei requisiti di leggibilità.

## 10. Criteri per valutare un'eventuale realizzazione

- Il logo è visibile all'apertura, ben proporzionato e leggibile in entrambi i temi.
- La prima schermata fa capire cosa cercare, a quale dispositivo inviare e dove premere.
- Con destinazione e dispositivo selezionati, l'invio resta accessibile anche quando il percorso iOS occupa molto spazio.
- Nome e indirizzo prevalgono sui dettagli tecnici; avvisi rilevanti restano visibili.
- Nessun ID, versione o branch lungo rompe il layout. Nessun nome di luogo viene troncato senza possibilità di leggerlo.
- Temi chiaro/scuro, testo ingrandito, tastiera e schermi piccoli non nascondono azioni o feedback. Contrasto obiettivo di almeno 4,5:1 per testo normale e 3:1 per testo grande e componenti rilevanti, da misurare sulle combinazioni definitive.
- Aree toccabili di almeno 44×44 pt su iOS e 48×48 dp su Android; etichette VoiceOver/TalkBack, focus e stato selezionato verificabili.
- Ricerca, condivisione, preferiti, selezione dispositivi e gestione credenziali mantengono i loro effetti reali. Gli errori non vengono sostituiti da indicatori decorativi.
- Verifica futura in simulatore Xcode obbligatoria per la UI iOS: home, dispositivi, preferiti, percorso ed estensione dove eseguibile, con screenshot ispezionati. Su Android verificare gli stessi stati applicabili in emulatore. I controlli su dispositivi fisici vanno riportati separatamente.

**Consegna attuale:** un solo documento di proposta condiviso per i due repository. Nessuna implementazione, commit, pubblicazione o modifica al comma.
