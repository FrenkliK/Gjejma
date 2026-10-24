# Gjejma 🇦🇱

**Gjej çmimin. Jo xhiron.** Gjejma është projekt me kod burimor të hapur për krahasimin e çmimeve në kohë reale nga dyqanet në internet në Shqipëri. Kërko një produkt, krahaso ofertat dhe hape listimin te shitësi. Gjejma nuk shet produkte dhe nuk përpunon pagesa.

Depoja e kodit përmban faqen e internetit, aplikacion për kompjuterët Windows dhe aplikacion Android. Kërkimet bëhen në kohë reale dhe rezultatet nuk ruhen në katalog ose llogari.

## Shkarkimet

Shkarko aplikacionet nga [publikimi më i fundit](https://github.com/FrenkliK/Gjejma/releases/latest):

- **Windows:** [Gjejma-Setup.exe](https://github.com/FrenkliK/Gjejma/releases/latest/download/Gjejma-Setup.exe). Instaloje dhe hape nga menyja Start. Nuk kërkohet Node.js, shkarkimi i depos së kodit apo server veçmas. Aktualisht ofrohet për Windows 64-bit.
- **Android:** [Gjejma.apk](https://github.com/FrenkliK/Gjejma/releases/latest/download/Gjejma.apk). Hape në telefon dhe ndiq udhëzimet e Android-it për instalim jashtë Play Store.

Të dy aplikacionet kërkojnë internet për kërkimet në kohë reale. Instaluesi Windows nuk është nënshkruar me certifikatë komerciale; Windows mund të kërkojë konfirmim para hapjes. Aplikacione për kompjuterët macOS dhe Linux nuk ofrohen ende.

## Si përdoret

1. Kërko emrin, markën ose modelin, p.sh. `Samsung`, `S26 Ultra`, `PS5`, `frigorifer` ose `biçikletë elektrike`.
2. Zgjidh filtër kategorie për rezultate më të sakta. Kërko `Samsung` dhe zgjidh **Telefona** për të hequr televizorët dhe elektroshtëpiaket Samsung.
3. Për `PS5`, zgjidh **Konzola** për të lënë jashtë lojërat dhe aksesorët që identifikohen në titullin e produktit.
4. Zgjidh **Të gjitha** për kërkim pa filtër kategorie.
5. Krahaso çmimin, dyqanin dhe stokun e raportuar. Hape ofertën te shitësi për të konfirmuar detajet para blerjes.

Filtrat janë të njëjtë në faqen e internetit dhe Android. Kategoritë përfshijnë Telefona, Konzola, Lojëra & aksesorë, TV & audio, pajisje të mëdha e të vogla shtëpiake, Ftohje & ngrohje, Kujdes personal, Kompjutera, Biçikleta & skuterë dhe Shtëpi & kopsht. Ndarja ndjek departamente të njohura të dyqaneve shqiptare, si Telefonia, TV & Audio, Ftohje & Ngrohje dhe Kompjutera.

Filtrimi përdor fjalë nga titulli i produktit. Titujt e paplotë mund të kategorizohen gabim; provo emrin e plotë ose **Të gjitha** nëse mungojnë rezultate.

## Nise faqen e internetit nga kodi burimor

Në Windows, instalo [Node.js 20 ose më të ri](https://nodejs.org/), shkarko depon e kodit dhe hape `start.bat`. Pastaj vizito [http://localhost:3000](http://localhost:3000). Nuk nevojitet `npm install`; shtyp `Ctrl+C` në dritaren e terminalit për ta ndalur serverin.

Në macOS ose Linux instalo Node.js 20 ose më të ri, shkarko depon e kodit dhe ekzekuto nga dosja e projektit:

```sh
sh ./start.sh
```

Pastaj vizito [http://localhost:3000](http://localhost:3000).

## Kërkimi dhe mbulimi

Gjejma kontrollon dyqanet e aktivizuara drejtpërdrejt gjatë kërkimit. Lista përfshin mbi 40 dyqane dhe platforma tregtare shqiptare me telefona, kompjutera, TV, pajisje shtëpiake, mobilie, lojëra, biçikleta dhe skuterë. Kategoritë e qarta ngushtojnë dyqanet që kontrollohen; **Të gjitha** kërkon në gjithë listën.

Mbulimi varet nga dyqani, interneti dhe formati i katalogut ose faqes së kërkimit. Disa faqe mund të kthejnë gabim HTTP, të mos kenë kërkim publik të lexueshëm ose të japin tituj të paplotë. Gjejma nuk anashkalon hyrjet, CAPTCHA-t apo mbrojtjet kundër bot-ëve. Disponueshmëria merret nga listimi dhe mund të jetë e vjetruar; konfirmoje te shitësi.

## Privatësia

- Nuk kërkohet llogari.
- Kërkimet në kohë reale nuk ruhen në telefon, serverin lokal apo bazë qendrore.
- Faqja e internetit ruan të preferuarat dhe objektivat e çmimit vetëm në `localStorage` të shfletuesit.
- Aplikacionet kontaktojnë faqet publike të dyqaneve për rezultatet; nevojitet internet.

## Funksionet

- Kërkim në kohë reale në dyqanet shqiptare të konfiguruara.
- Filtra opsionalë kategorish në faqen e internetit dhe Android.
- Shkurtesa dhe emra alternativë, p.sh. `PS5` / `PlayStation 5`.
- Terma shqip e anglisht dhe tolerancë për një gabim të vogël shkrimi.
- Renditje sipas çmimit ose dyqanit; shfaqje e ofertës dhe stokut të raportuar.
- Të preferuara dhe objektiva çmimi të ruajtura në shfletues.
- Android pa server të Gjejma-s; Windows pa instalim të Node.js.
- Ndërfaqe dhe dokumentim në shqip.

## Zhvillimi i faqes së internetit

Kërkohet Node.js 20 ose më i ri. Faqja e internetit nuk ka nevojë për paketa npm shtesë:

```sh
node server/server.js
```

Hape [http://localhost:3000](http://localhost:3000). Për portë tjetër cakto variablën `PORT`. Kërkimet në kohë reale nuk lexojnë ose përditësojnë `server/db.json`.

Përshtatësit provojnë API-në e WooCommerce Store, sugjerimet e Shopify, faqet e kërkimit të dyqaneve, të dhënat JSON-LD dhe kartat e produkteve. Mbledhësi i vjetër i katalogut është opsional; `AUTO_COLLECT=true` e aktivizon.

### Aplikacioni Windows

Përdoruesi i instaluesit nuk ka nevojë për Node.js. Për zhvillim/paketim duhen Windows, Node.js 22 dhe Electron Forge:

```powershell
cd desktop
npm install
npm start
npm run make
```

Instaluesi prodhohet te `desktop/out/make/`. GitHub Actions e publikon bashkë me APK-në kur ndryshon kodi i faqes së internetit, serverit, Android-it ose aplikacionit Windows. Mjedisi i ekzekutimit dhe Chromium përfshihen në paketë; kërkimet kërkojnë internet.

### Aplikacioni Android

Hape `android/` në Android Studio me JDK 17 dhe Android SDK Platform 36. Projekti përdor Gradle 9.3.1 dhe nuk përfshin skedarët binarë të Gradle Wrapper. Me Gradle 9.3.1 të instaluar:

```sh
gradle -p android assembleDebug
```

APK-ja krijohet te `android/app/build/outputs/apk/debug/app-debug.apk`. Kjo APK është për instalim/provë jashtë Play Store. Publikimi në Play Store kërkon çelës nënshkrimi dhe konfigurim të veçantë. Mos i ruaj çelësat ose fjalëkalimet në depon e kodit.

## Struktura e depos së kodit

Kodi ndahet në `web/` (faqja e internetit), `server/` (API-ja, kërkimi, dyqanet dhe konfigurimi), `desktop/` (aplikacioni Windows), `android/` (aplikacioni Android), `docs/` (arkitektura) dhe `.github/workflows/` (ndërtimet automatike). `start.bat` dhe `start.sh` nisin faqen e internetit nga kodi burimor; `collect.bat` nis mbledhësin opsional të vjetër.

## API-ja lokale

API-ja lokale ofrohet nga serveri Node.js:

- `GET /api/health` — gjendja e serverit dhe katalogut të vjetër.
- `GET /api/shops` — lista dhe statusi i dyqaneve.
- `GET /api/categories` — emrat dhe rregullat e filtrave.
- `GET /api/search?q=iphone` — kërkim pa filtër kategorie.
- `GET /api/search?q=samsung&category=phones` — kërkim me filtër telefoni.
- `GET /api/products?limit=100&offset=0&q=iphone` — faqe nga katalogu opsional i vjetër.
- `GET /api/products/:id` — produkti, ofertat dhe historiku i katalogut të vjetër.
- `GET /api/history/:id` — historiku i çmimeve të katalogut të vjetër.

Parametri `category` pranon çelësat e kthyer nga `/api/categories`. Kërkimet në kohë reale nuk ruhen në katalog. `POST /api/collect` përdoret vetëm nga mbledhësi i vjetër lokal.

## Kontributet

1. Shto dyqane te `server/stores.json`, me identifikues unik, domen të saktë, përshtatës dhe etiketa kategorish.
2. Shto emra alternativë ose rregulla filtrimi te `server/search-config.json`; filtrat shfaqen automatikisht në faqen e internetit dhe Android.
3. Për dyqane me strukturë të veçantë, përmirëso përshtatësin te `server/collector.js`.
4. Përditëso udhëzimet kur ndryshon sjellja.

Mos anashkalo hyrjet, CAPTCHA-t ose kontrollet kundër bot-ëve. Për dyqanet që nuk lejojnë kërkim automatik, kërko bashkëpunim të autorizuar.

## Licenca

Gjejma shpërndahet sipas licencës MIT. Shiko [LICENSE](LICENSE).

