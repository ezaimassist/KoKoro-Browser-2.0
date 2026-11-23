# PulseBrowser per Android
Browser ARM64 con motore Chromium integrato tramite Cefrium 0.21.0. Il progetto adatta il browser Android di riferimento di Cefrium (https://codeberg.org/cefrium/cefrium-browser). La licenza dei sorgenti di base è inclusa in LICENSE. Questa variante usa un identificatore Android distinto.

## Compilazione
Usare JDK 25, Android SDK 37.0 e il Gradle Wrapper incluso. Dalla cartella del progetto eseguire `./gradlew :app:assembleDebug`. Gradle recupera il motore dal repository Maven di Cefrium. L'APK ARM64 viene scritto in app/build/outputs/apk/debug/.

## Controlli
Nel menu ⋮ si trovano la posizione e la densità della barra, il tema, lo schermo intero, la frequenza display e il collegamento alla pagina ufficiale di Last Havens. Per uscire dallo schermo intero manuale usare Indietro. La modalità frequenza richiede la modalità disponibile più veloce fino a 120 Hz alla risoluzione corrente. L'impostazione di sistema è quella iniziale.

## Origine degli adattamenti
Bare Browser v2.0.5 distribuisce patch per il proprio checkout Chromium. Le opzioni di posizione e compattezza dei controlli sono implementate qui in Kotlin sul modello della barra configurabile di Bare. Il binario del motore proviene da Cefrium e si aggiorna cambiando dipendenza e ricompilando l'app. La build conserva navigazione, schede, sessioni, download e cronologia del progetto Cefrium. Per verificare la fluidità del gioco occorre confrontare gli FPS del gioco durante la stessa rotazione della camera, alla stessa scala e nello stesso punto della mappa.

## Versione 0.2.0
Il menu PulseBrowser distingue l'avvio normale, la pagina del gioco con solo il contatore FPS e la diagnostica con log. Con il profilo ad alta frequenza, da Android 15 viene impostata anche la frequenza richiesta dalla vista della finestra. Il menu riporta fino a sei variazioni consecutive degli Hz comunicate da Android per la scheda attiva. Il campionamento si ferma quando si apre il menu o si lascia l'app. Il valore riguarda il display e gli FPS si leggono nel gioco.

## Versione 0.2.1
Con la modalità ad alta frequenza attiva e Last Havens aperto su /play, la richiesta viene inviata anche alla SurfaceView visibile più grande su Android 11 o successivo. Il menu mostra dove è stata inviata. Il limite supportato viene scelto alla risoluzione corrente. La preferenza della superficie viene rimossa uscendo dal gioco o disattivando la modalità. I collegamenti al gioco riutilizzano una scheda già aperta sullo stesso URL nella medesima modalità di navigazione.
